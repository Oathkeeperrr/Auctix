package com.auctix.repository;

import com.auctix.domain.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    List<ItemImage> findByItemIdOrderBySortOrderAsc(Long itemId);

    long countByItemId(Long itemId);

    /** Item ids that currently have no gallery rows at all. Drives the one-off backfill. */
    @Query("select i.id from Item i where not exists "
            + "(select 1 from ItemImage img where img.item.id = i.id)")
    List<Long> findItemIdsWithoutImages();

    @Query("select img from ItemImage img where img.item.id in :itemIds order by img.sortOrder asc")
    List<ItemImage> findForItems(@Param("itemIds") List<Long> itemIds);
}
