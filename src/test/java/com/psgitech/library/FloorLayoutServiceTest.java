package com.psgitech.library;

import com.psgitech.library.model.HighDemandAlert;
import com.psgitech.library.model.LibraryLayout;
import com.psgitech.library.repository.FloorLayoutRepository;
import com.psgitech.library.service.FloorLayoutService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FloorLayoutServiceTest {

    private FloorLayoutService service;
    private MockFloorLayoutRepo mockRepo;

    static class MockFloorLayoutRepo extends FloorLayoutRepository {
        List<String> floors = List.of("Floor 1", "Floor 2");
        List<LibraryLayout> layoutCells = new ArrayList<>();
        List<HighDemandAlert> alerts = new ArrayList<>();

        @Override
        public List<String> findFloors() {
            return floors;
        }

        @Override
        public List<LibraryLayout> findLayoutWithHeatmap(String floor) {
            return layoutCells;
        }

        @Override
        public List<HighDemandAlert> findHighDemandAlerts(int queueThreshold, double shortageRatio) {
            return alerts;
        }
    }

    @Before
    public void setUp() {
        mockRepo = new MockFloorLayoutRepo();
        service = new FloorLayoutService(mockRepo);
    }

    @Test
    public void testGetFloorsReturnsDistinctFloors() {
        List<String> result = service.getFloors();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("Floor 1"));
    }

    @Test
    public void testGetFloorHeatmapReturnsDensityLevels() {
        LibraryLayout rack1 = new LibraryLayout();
        rack1.setFloor("Floor 1");
        rack1.setRack("R1");
        rack1.setLabel("Rack A-1");
        rack1.setTotalCopies(10);
        rack1.setActiveBorrowCount(8);
        rack1.setFillPercentage(80.0);
        rack1.setDensityLevel("HIGH");

        LibraryLayout rack2 = new LibraryLayout();
        rack2.setFloor("Floor 1");
        rack2.setRack("R2");
        rack2.setLabel("Rack A-2");
        rack2.setTotalCopies(10);
        rack2.setActiveBorrowCount(1);
        rack2.setFillPercentage(10.0);
        rack2.setDensityLevel("LOW");

        mockRepo.layoutCells = List.of(rack1, rack2);

        List<LibraryLayout> heatmap = service.getFloorHeatmap("Floor 1");
        assertNotNull(heatmap);
        assertEquals(2, heatmap.size());
        assertEquals("HIGH", heatmap.get(0).getDensityLevel());
        assertEquals("LOW", heatmap.get(1).getDensityLevel());
    }

    @Test
    public void testGetHighDemandAlertsDetection() {
        HighDemandAlert alert = new HighDemandAlert();
        alert.setBookId(1);
        alert.setTitle("Introduction to Algorithms");
        alert.setWaitingCount(6);
        alert.setTotalCopies(2);
        alert.setAvailableCopies(0);
        alert.setShortageRatio(3.0);
        alert.setRecommendation("Critical: Zero available copies with 6 students queued.");

        mockRepo.alerts = List.of(alert);

        List<HighDemandAlert> alerts = service.getHighDemandAlerts();
        assertNotNull(alerts);
        assertEquals(1, alerts.size());
        assertEquals(6, alerts.get(0).getWaitingCount());
        assertEquals(3.0, alerts.get(0).getShortageRatio(), 0.001);
        assertTrue(alerts.get(0).getRecommendation().contains("Critical"));
    }
}
