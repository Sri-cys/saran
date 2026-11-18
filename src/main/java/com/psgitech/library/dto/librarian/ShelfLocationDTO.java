package com.psgitech.library.dto.librarian;

import java.io.Serializable;

/**
 * Data Transfer Object for Smart Shelf Locator.
 */
public class ShelfLocationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int locationId;
    private int bookId;
    private Integer copyId;
    private String floor;
    private String block;
    private String section;
    private String rack;
    private String shelf;

    private String bookTitle;
    private String isbn;
    private String copyCode;

    public ShelfLocationDTO() {}

    public int getLocationId() { return locationId; }
    public void setLocationId(int locationId) { this.locationId = locationId; }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public Integer getCopyId() { return copyId; }
    public void setCopyId(Integer copyId) { this.copyId = copyId; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getRack() { return rack; }
    public void setRack(String rack) { this.rack = rack; }

    public String getShelf() { return shelf; }
    public void setShelf(String shelf) { this.shelf = shelf; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getCopyCode() { return copyCode; }
    public void setCopyCode(String copyCode) { this.copyCode = copyCode; }

    public String getFullLocationString() {
        return (floor != null ? floor : "") + " | " +
               (block != null ? block : "") + " | " +
               (section != null ? section : "") + " | Rack: " +
               (rack != null ? rack : "") + " | Shelf: " +
               (shelf != null ? shelf : "");
    }
}
