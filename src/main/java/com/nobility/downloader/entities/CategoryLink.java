package com.nobility.downloader.entities;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class CategoryLink {

    @Id
    public long id;
    public String url;
    public int type;

    public CategoryLink() {}

    public CategoryLink(String url, int type) {
        this.url = url;
        this.type = type;
    }
}
