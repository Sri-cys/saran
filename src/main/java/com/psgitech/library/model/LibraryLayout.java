package com.psgitech.library.model;

import java.sql.Timestamp;

/**
 * Model representing 2D floor grid layout and rack coordinates for interactive library heatmap.
 * Table: library_layout.
 */
public class LibraryLayout {
    private Integer id;
    private String floor;
    private String block;
    private String section;
    private String rack;
    private int gridRow;
    private int gridCol;
    private int rowSpan = 1;
    private int colSpan = 1;
    private String label;
    private String categoryHint;
    private Timestamp updatedAt;

    // Heatmap & circulation metrics
    private int activeBorrowCount = 0;
    private int totalCopies = 0;
    private double fillPercentage = 0.0;
    private String densityLevel = "LOW"; // LOW, MEDIUM, HIGH

    public LibraryLayout() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getRack() {
        return rack;
    }

    public void setRack(String rack) {
        this.rack = rack;
    }

    public int getGridRow() {
        return gridRow;
    }

    public void setGridRow(int gridRow) {
        this.gridRow = gridRow;
    }

    public int getGridCol() {
        return gridCol;
    }

    public void setGridCol(int gridCol) {
        this.gridCol = gridCol;
    }

    public int getRowSpan() {
        return rowSpan;
    }

    public void setRowSpan(int rowSpan) {
        this.rowSpan = rowSpan;
    }

    public int getColSpan() {
        return colSpan;
    }

    public void setColSpan(int colSpan) {
        this.colSpan = colSpan;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCategoryHint() {
        return categoryHint;
    }

    public void setCategoryHint(String categoryHint) {
        this.categoryHint = categoryHint;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getActiveBorrowCount() {
        return activeBorrowCount;
    }

    public void setActiveBorrowCount(int activeBorrowCount) {
        this.activeBorrowCount = activeBorrowCount;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(int totalCopies) {
        this.totalCopies = totalCopies;
    }

    public double getFillPercentage() {
        return fillPercentage;
    }

    public void setFillPercentage(double fillPercentage) {
        this.fillPercentage = fillPercentage;
    }

    public String getDensityLevel() {
        return densityLevel;
    }

    public void setDensityLevel(String densityLevel) {
        this.densityLevel = densityLevel;
    }
}
