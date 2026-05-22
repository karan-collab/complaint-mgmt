package com.societycare.common;

import com.societycare.complaint.Category;
import com.societycare.status.Status;
import com.societycare.status.StatusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final StatusRepository statusRepository;

    public MetaController(StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return Arrays.stream(Category.values())
                .map(c -> new CategoryView(c.name(), c.getLabel()))
                .collect(Collectors.toList());
    }

    @GetMapping("/statuses")
    public List<StatusView> statuses() {
        return statusRepository.findAll().stream()
                .sorted(Comparator.comparing(Status::getStatusId))
                .map(s -> new StatusView(s.getStatusId(), s.getStatusName()))
                .collect(Collectors.toList());
    }

    public static class CategoryView {

        private final String id;
        private final String label;

        public CategoryView(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class StatusView {

        private final Integer id;
        private final String name;

        public StatusView(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
