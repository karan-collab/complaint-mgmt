package com.societycare.professional;

import com.societycare.complaint.Category;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "t_professional")
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private Category category;

    public Professional() {
    }

    public Professional(String name, String phone, Category category) {
        this.name = name;
        this.phone = phone;
        this.category = category;
    }

    public Long getProfessionalId() {
        return professionalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
