package com.psgitech.library.dto.librarian;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Data Transfer Object for Student Library Clearance / No-Dues Certificate.
 */
public class NoDuesSlipDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int studentId;
    private String studentName;
    private String registerNumber;
    private String department;
    private Integer year;
    private String email;

    private int activeLoansCount;
    private BigDecimal unpaidFinesAmount;
    private int pendingTransfersCount;
    private boolean eligible;

    private int issuedByStaffId;
    private String issuedByStaffName;
    private String issuedTimestamp;
    private String certificateReference;

    public NoDuesSlipDTO() {}

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getActiveLoansCount() { return activeLoansCount; }
    public void setActiveLoansCount(int activeLoansCount) { this.activeLoansCount = activeLoansCount; }

    public BigDecimal getUnpaidFinesAmount() { return unpaidFinesAmount; }
    public void setUnpaidFinesAmount(BigDecimal unpaidFinesAmount) { this.unpaidFinesAmount = unpaidFinesAmount; }

    public int getPendingTransfersCount() { return pendingTransfersCount; }
    public void setPendingTransfersCount(int pendingTransfersCount) { this.pendingTransfersCount = pendingTransfersCount; }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public int getIssuedByStaffId() { return issuedByStaffId; }
    public void setIssuedByStaffId(int issuedByStaffId) { this.issuedByStaffId = issuedByStaffId; }

    public String getIssuedByStaffName() { return issuedByStaffName; }
    public void setIssuedByStaffName(String issuedByStaffName) { this.issuedByStaffName = issuedByStaffName; }

    public String getIssuedTimestamp() { return issuedTimestamp; }
    public void setIssuedTimestamp(String issuedTimestamp) { this.issuedTimestamp = issuedTimestamp; }

    public String getCertificateReference() { return certificateReference; }
    public void setCertificateReference(String certificateReference) { this.certificateReference = certificateReference; }
}
