package com.brunosong.actuator.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "lock_resource")
public class LockResource {

    @Id
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "counter_val", nullable = false)
    private long counter;

    protected LockResource() {
    }

    public LockResource(Long id, String name) {
        this.id = id;
        this.name = name;
        this.counter = 0L;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCounter() {
        return counter;
    }

    public void increment() {
        this.counter++;
    }
}