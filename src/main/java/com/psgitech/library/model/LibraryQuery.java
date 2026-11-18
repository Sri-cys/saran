package com.psgitech.library.model;

import java.sql.Timestamp;

public class LibraryQuery {
    private Integer queryId;
    private int studentId;
    private String studentName;
    private String studentRegNo;
    private String category;
    private String subject;
    private String message;
    private String status; // OPEN, RESOLVED
    private String responseText;
    private Integer resolvedBy;
    private String resolverName;
    private Timestamp resolvedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public LibraryQuery() {}

    public Integer getQueryId() { return queryId; }
    public void setQueryId(Integer queryId) { this.queryId = queryId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentRegNo() { return studentRegNo; }
    public void setStudentRegNo(String studentRegNo) { this.studentRegNo = studentRegNo; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolverName() { return resolverName; }
    public void setResolverName(String resolverName) { this.resolverName = resolverName; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
