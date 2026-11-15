package com.psgitech.library.policy;

import com.psgitech.library.model.BookPolicyState;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.repository.LoanPolicyRepository;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Domain coordinator for dynamic loan policy resolution and display.
 * Corresponds to BorrowingPolicyManager in the original architectural specification.
 * Integrates the Strategy pattern, snapshot locking reads, and hysteresis persistence.
 */
public class LoanPolicyManager {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyManager.class.getName());

    private static final LoanPolicyManager INSTANCE = new LoanPolicyManager();

    private final LoanPolicyRepository repository;

    public static LoanPolicyManager getInstance() {
        return INSTANCE;
    }

    public LoanPolicyManager() {
        this(new LoanPolicyRepository());
    }

    public LoanPolicyManager(LoanPolicyRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves the policy to apply for a new loan during issue or collection.
     * MUST be invoked within an active JDBC transaction while holding the books row lock.
     * Reads current state FOR UPDATE, counts demand with FOR SHARE, applies hysteresis,
     * persists any state change, and returns the concrete LoanPolicy strategy.
     *
     * @param conn       Active JDBC connection holding the books lock
     * @param bookId     ID of the book being issued
     * @param borrowerId ID of the student receiving the book
     * @return Concrete LoanPolicy strategy (StandardLoanPolicy or HighDemandLoanPolicy)
     * @throws SQLException on database failure
     */
    public LoanPolicy resolveForIssue(Connection conn, int bookId, int borrowerId) throws SQLException {
        // 1. Read settings
        LoanPolicySettings settings = repository.getSettings(conn);
        if (settings == null || !settings.isEnabled()) {
            return StandardLoanPolicy.getInstance();
        }

        // Check for exam-season window override (Stretch D12)
        Integer override = repository.getActiveWindowOverride(conn, LocalDate.now());
        if (override != null) {
            settings = settings.withEnterThresholdOverride(override);
        }

        // 2. Read persistent hysteresis state FOR UPDATE
        BookPolicyState state = repository.getBookPolicyState(conn, bookId, true);
        String currentState = (state != null) ? state.getPolicyCode() : StandardLoanPolicy.CODE;

        // 3. Locking read for demand: other waiting or allocated students (Decision D1)
        int demand = repository.countDemandForBook(conn, bookId, borrowerId, true);

        // 4. Apply pure transition rule
        String nextState = PolicyTransitionRule.next(currentState, demand, settings);
        boolean stateChanged = !nextState.equalsIgnoreCase(currentState);

        // 5. Persist state and last evaluated demand
        repository.upsertBookPolicyState(conn, bookId, nextState, demand, stateChanged);

        if (HighDemandLoanPolicy.CODE.equalsIgnoreCase(nextState)) {
            return new HighDemandLoanPolicy(settings.getHighDemandDays());
        }
        return StandardLoanPolicy.getInstance();
    }

    /**
     * Evaluates effective policy for display without performing any database writes.
     *
     * @param bookId Target book ID
     * @return Effective LoanPolicy
     */
    public LoanPolicy effectivePolicy(int bookId) {
        try (Connection conn = DBConnection.getConnection()) {
            LoanPolicySettings settings = repository.getSettings(conn);
            if (settings == null || !settings.isEnabled()) {
                return StandardLoanPolicy.getInstance();
            }

            Integer override = repository.getActiveWindowOverride(conn, LocalDate.now());
            if (override != null) {
                settings = settings.withEnterThresholdOverride(override);
            }

            BookPolicyState state = repository.getBookPolicyState(conn, bookId, false);
            String currentState = (state != null) ? state.getPolicyCode() : StandardLoanPolicy.CODE;
            int demand = repository.countDemandForBookReadOnly(conn, bookId);

            String nextState = PolicyTransitionRule.next(currentState, demand, settings);
            if (HighDemandLoanPolicy.CODE.equalsIgnoreCase(nextState)) {
                return new HighDemandLoanPolicy(settings.getHighDemandDays());
            }
            return StandardLoanPolicy.getInstance();

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to resolve effective policy for book #" + bookId + ", falling back to standard", e);
            return StandardLoanPolicy.getInstance();
        }
    }

    /**
     * Evaluates effective policy inside an existing transaction connection (read-only, e.g. for renewal).
     */
    public LoanPolicy effectivePolicy(Connection conn, int bookId) throws SQLException {
        LoanPolicySettings settings = repository.getSettings(conn);
        if (settings == null || !settings.isEnabled()) {
            return StandardLoanPolicy.getInstance();
        }

        Integer override = repository.getActiveWindowOverride(conn, LocalDate.now());
        if (override != null) {
            settings = settings.withEnterThresholdOverride(override);
        }

        BookPolicyState state = repository.getBookPolicyState(conn, bookId, false);
        String currentState = (state != null) ? state.getPolicyCode() : StandardLoanPolicy.CODE;
        // Locking read for demand per MySQL REPEATABLE READ snapshot rule
        int demand = repository.countDemandForBook(conn, bookId, -1, true);

        String nextState = PolicyTransitionRule.next(currentState, demand, settings);
        if (HighDemandLoanPolicy.CODE.equalsIgnoreCase(nextState)) {
            return new HighDemandLoanPolicy(settings.getHighDemandDays());
        }
        return StandardLoanPolicy.getInstance();
    }

    /**
     * Batched read-only evaluation for catalogue and search result cards.
     * Executes exactly 3 SQL statements regardless of list size: 0 N+1 overhead.
     *
     * @param bookIds List of book IDs
     * @return Map of bookId -> LoanPolicy
     */
    public Map<Integer, LoanPolicy> effectivePolicies(List<Integer> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try (Connection conn = DBConnection.getConnection()) {
            LoanPolicySettings settings = repository.getSettings(conn);
            if (settings == null || !settings.isEnabled()) {
                Map<Integer, LoanPolicy> result = new HashMap<>();
                for (Integer id : bookIds) {
                    result.put(id, StandardLoanPolicy.getInstance());
                }
                return result;
            }

            Integer override = repository.getActiveWindowOverride(conn, LocalDate.now());
            if (override != null) {
                settings = settings.withEnterThresholdOverride(override);
            }

            Map<Integer, BookPolicyState> stateMap = repository.getBookPolicyStatesBatched(conn, bookIds);
            Map<Integer, Integer> demandMap = repository.countDemandBatched(conn, bookIds);

            Map<Integer, LoanPolicy> result = new HashMap<>();
            for (Integer id : bookIds) {
                BookPolicyState state = stateMap.get(id);
                String currentState = (state != null) ? state.getPolicyCode() : StandardLoanPolicy.CODE;
                int demand = demandMap.getOrDefault(id, 0);

                String nextState = PolicyTransitionRule.next(currentState, demand, settings);
                if (HighDemandLoanPolicy.CODE.equalsIgnoreCase(nextState)) {
                    result.put(id, new HighDemandLoanPolicy(settings.getHighDemandDays()));
                } else {
                    result.put(id, StandardLoanPolicy.getInstance());
                }
            }
            return result;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to resolve batched effective policies, falling back to standard", e);
            Map<Integer, LoanPolicy> result = new HashMap<>();
            for (Integer id : bookIds) {
                result.put(id, StandardLoanPolicy.getInstance());
            }
            return result;
        }
    }

    /**
     * Returns effective loan duration in days for the waiting-time estimator (Feature 4).
     */
    public int effectiveLoanDays(int bookId) {
        return effectivePolicy(bookId).getLoanDays();
    }
}
