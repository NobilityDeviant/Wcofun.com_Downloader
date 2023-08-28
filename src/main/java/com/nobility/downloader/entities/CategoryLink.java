package com.nobility.downloader.entities;

import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class CategoryLink {

    @Id
    public long id;
    @Deprecated
    public String url;
    public String slug;
    public int type;

    public CategoryLink() {}

    public CategoryLink(String slug, int type) {
        this.type = type;
        this.slug = slug;
    }

    public void updateSlug() {
        if (!StringChecker.isNullOrEmpty(url) && StringChecker.isNullOrEmpty(slug)) {
            this.slug = Tools.extractSlugFromLink(url);
        }
    }
}
