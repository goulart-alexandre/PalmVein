package com.hfims.android.lib.palm.model;

public class User {
    private Long id;
    private String name;
    private String userId;
    private String palmFeature;
    private long createdAt;

    public User() {
        this.createdAt = System.currentTimeMillis();
    }

    public User(String name, String userId, String palmFeature) {
        this.name = name;
        this.userId = userId;
        this.palmFeature = palmFeature;
        this.createdAt = System.currentTimeMillis();
    }

    public User(String name, String userId, String palmFeature, long createdAt) {
        this.name = name;
        this.userId = userId;
        this.palmFeature = palmFeature;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPalmFeature() {
        return palmFeature;
    }

    public void setPalmFeature(String palmFeature) {
        this.palmFeature = palmFeature;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", userId='" + userId + '\'' +
                ", palmFeature='" + (palmFeature != null ? "Coletada" : "Não coletada") + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
