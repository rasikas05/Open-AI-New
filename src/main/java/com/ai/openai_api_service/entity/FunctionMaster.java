package com.ai.openai_api_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "FUNCTION_MASTER",
       uniqueConstraints = @UniqueConstraint(columnNames = {"mnid", "mnvr", "fnid"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FunctionMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10)
    private String mnid;

    @Column(length = 10)
    private String mnvr;

    private Long lmts;

    private Integer levl;

    @Column(length = 10)
    private String fnid;

    @Column(length = 3)
    private String fnt3;

    @Column(length = 40)
    private String tx40;

    @Column(length = 7)
    private String msid;

    @Column(length = 3)
    private String mnop;

    @Column(length = 60)
    private String mash;

    @Column(length = 60)
    private String maon;

    @Column(length = 60)
    private String mdev;

    @Column(length = 256)
    private String urla;

    @Column(length = 800)
    private String pame;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
