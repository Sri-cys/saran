package com.psgitech.library.service;

import com.psgitech.library.model.HighDemandAlert;
import com.psgitech.library.model.LibraryLayout;
import com.psgitech.library.repository.FloorLayoutRepository;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating 2D Floor Layout grid, Shelf Heatmap analytics, and High Demand alerts.
 */
public class FloorLayoutService {
    private static final Logger LOGGER = Logger.getLogger(FloorLayoutService.class.getName());

    private final FloorLayoutRepository repository;

    public FloorLayoutService() {
        this.repository = new FloorLayoutRepository();
    }

    public FloorLayoutService(FloorLayoutRepository repository) {
        this.repository = repository;
    }

    public List<String> getFloors() {
        return repository.findFloors();
    }

    public List<LibraryLayout> getFloorHeatmap(String floor) {
        return repository.findLayoutWithHeatmap(floor);
    }

    public List<HighDemandAlert> getHighDemandAlerts() {
        int threshold = getConfigInt("high_demand_queue_threshold", 4);
        double ratio = getConfigDouble("copy_shortage_ratio", 2.0);
        return repository.findHighDemandAlerts(threshold, ratio);
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = getConfigString(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private double getConfigDouble(String key, double defaultValue) {
        String val = getConfigString(key);
        if (val != null) {
            try {
                return Double.parseDouble(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private String getConfigString(String key) {
        String sql = "SELECT setting_value FROM config_settings WHERE setting_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error querying config_settings for key: " + key, e);
        }
        return null;
    }
}
