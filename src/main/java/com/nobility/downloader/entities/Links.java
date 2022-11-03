package com.nobility.downloader.entities;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

import java.util.List;

@Entity
public class Links {

    @Id
    public long id;
    public List<String> dubbed;
    public List<String> subbed;
    public List<String> cartoons;
    public List<String> movies;

    public Links() {}

    public Links(List<String> dubbed, List<String> subbed, List<String> cartoons, List<String> movies) {
        this.dubbed = dubbed;
        this.subbed = subbed;
        this.cartoons = cartoons;
        this.movies = movies;
    }
}
