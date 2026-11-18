package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.repository.ShelfLocatorRepository;

import java.util.List;

/**
 * Service managing book physical location on shelves and racks.
 */
public class ShelfLocatorService {
    private final ShelfLocatorRepository repository;

    public ShelfLocatorService() {
        this.repository = new ShelfLocatorRepository();
    }

    public ShelfLocatorService(ShelfLocatorRepository repository) {
        this.repository = repository;
    }

    public ShelfLocationDTO getLocationForBook(int bookId) {
        return repository.findByBookId(bookId);
    }

    public ShelfLocationDTO getLocationForCopy(int copyId) {
        return repository.findByCopyId(copyId);
    }

    public boolean updateLocation(int bookId, Integer copyId, String floor, String block,
                                  String section, String rack, String shelf) {
        if (floor == null || floor.trim().isEmpty()) throw new ValidationException("Floor is required.");
        if (block == null || block.trim().isEmpty()) throw new ValidationException("Block is required.");
        if (section == null || section.trim().isEmpty()) throw new ValidationException("Section is required.");
        if (rack == null || rack.trim().isEmpty()) throw new ValidationException("Rack number is required.");
        if (shelf == null || shelf.trim().isEmpty()) throw new ValidationException("Shelf number is required.");

        return repository.upsertLocation(bookId, copyId, floor.trim(), block.trim(),
                section.trim(), rack.trim(), shelf.trim());
    }

    public List<ShelfLocationDTO> search(String query, int page, int pageSize) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return repository.search(query, offset, clampedSize);
    }
}
