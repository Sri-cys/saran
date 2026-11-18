package com.psgitech.library.service;

import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.StudentMaster;
import com.psgitech.library.repository.StudentRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Service managing the verified Student Master Directory and peer interchange lookups.
 */
public class StudentDirectoryService {
    private static final Logger LOGGER = Logger.getLogger(StudentDirectoryService.class.getName());
    private final StudentRepository studentRepository;

    public StudentDirectoryService() {
        this.studentRepository = new StudentRepository();
    }

    public StudentDirectoryService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Map<String, Object> getDirectory(String query, String registrationStatus,
                                           String accountStatus, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;

        int totalRecords = studentRepository.count(query, registrationStatus, accountStatus);
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        if (totalPages == 0) totalPages = 1;

        List<StudentMaster> students = studentRepository.search(query, registrationStatus, accountStatus, page, pageSize);
        Map<String, Integer> metrics = studentRepository.getMetrics();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("students", students);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("pageSize", pageSize);
        pagination.put("totalRecords", totalRecords);
        pagination.put("totalPages", totalPages);
        result.put("pagination", pagination);
        result.put("metrics", metrics);

        return result;
    }

    public Map<String, Integer> getMetrics() {
        return studentRepository.getMetrics();
    }

    public StudentMaster getStudentByRollNumber(String rollNumber) {
        return studentRepository.findByRollNumber(rollNumber);
    }

    /**
     * Retrieves full student profile along with active book holdings for peer interchange.
     */
    public StudentMaster getStudentProfile(String rollNumber) {
        StudentMaster master = studentRepository.findByRollNumber(rollNumber);
        if (master != null && master.getUserId() != null) {
            List<BorrowTransaction> holdings = studentRepository.getActiveBorrowings(master.getUserId());
            master.setActiveBorrowings(holdings);
            master.setActiveBorrowingsCount(holdings.size());
        }
        return master;
    }

    /**
     * Verifies roll number against institutional master list for registration form.
     */
    public Map<String, Object> verifyRollNumber(String rollNumber) {
        Map<String, Object> res = new HashMap<>();
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            res.put("valid", false);
            res.put("message", "Roll number cannot be empty.");
            return res;
        }

        StudentMaster student = studentRepository.findByRollNumber(rollNumber.trim());
        if (student == null) {
            res.put("valid", false);
            res.put("existsInMaster", false);
            res.put("message", "Roll number '" + rollNumber + "' was not found in the institutional master directory.");
            return res;
        }

        res.put("valid", true);
        res.put("existsInMaster", true);
        res.put("fullName", student.getFullName());
        res.put("accountStatus", student.getAccountStatus());
        res.put("registrationStatus", student.getRegistrationStatus());
        res.put("alreadyRegistered", student.isRegistered());
        res.put("active", student.isActive());

        if (student.isRegistered()) {
            res.put("message", "This roll number is already registered. Please proceed to login.");
        } else if (!student.isActive()) {
            res.put("message", "Institutional record is inactive. Please contact the library administrator.");
        } else {
            res.put("message", "Institutional record verified: " + student.getFullName());
        }

        return res;
    }
}
