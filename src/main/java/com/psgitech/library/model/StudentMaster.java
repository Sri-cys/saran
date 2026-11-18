package com.psgitech.library.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a verified student in the Institutional Student Master Directory.
 * Serves as the single source of truth for student identity at PSG iTech.
 */
public class StudentMaster implements Serializable {
    private static final long serialVersionUID = 1L;

    private int studentId;
    private String permanentRollNumber;
    private String fullName;
    private String accountStatus;      // 'ACTIVE', 'INACTIVE'
    private String registrationStatus; // 'NOT_REGISTERED', 'REGISTERED'
    private Integer userId;            // FK to users.user_id, null if not registered
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Enriched fields from linked User account (when registered)
    private String email;
    private String department;
    private Integer year;
    private String phone;
    private int activeBorrowingsCount;
    private List<BorrowTransaction> activeBorrowings = new ArrayList<>();

    public StudentMaster() {
        this.accountStatus = "ACTIVE";
        this.registrationStatus = "NOT_REGISTERED";
    }

    public StudentMaster(int studentId, String permanentRollNumber, String fullName,
                         String accountStatus, String registrationStatus, Integer userId) {
        this.studentId = studentId;
        this.permanentRollNumber = permanentRollNumber;
        this.fullName = fullName;
        this.accountStatus = accountStatus;
        this.registrationStatus = registrationStatus;
        this.userId = userId;
    }

    // Getters and Setters

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getPermanentRollNumber() {
        return permanentRollNumber;
    }

    public void setPermanentRollNumber(String permanentRollNumber) {
        this.permanentRollNumber = permanentRollNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isRegistered() {
        return "REGISTERED".equalsIgnoreCase(this.registrationStatus);
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.accountStatus);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getActiveBorrowingsCount() {
        return activeBorrowingsCount;
    }

    public void setActiveBorrowingsCount(int activeBorrowingsCount) {
        this.activeBorrowingsCount = activeBorrowingsCount;
    }

    public List<BorrowTransaction> getActiveBorrowings() {
        return activeBorrowings;
    }

    public void setActiveBorrowings(List<BorrowTransaction> activeBorrowings) {
        this.activeBorrowings = activeBorrowings;
    }

    @Override
    public String toString() {
        return "StudentMaster{" +
                "studentId=" + studentId +
                ", permanentRollNumber='" + permanentRollNumber + '\'' +
                ", fullName='" + fullName + '\'' +
                ", accountStatus='" + accountStatus + '\'' +
                ", registrationStatus='" + registrationStatus + '\'' +
                ", userId=" + userId +
                '}';
    }
}
