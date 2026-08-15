package com.lab.labtimesheet.feature.project.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcOperations;

class ProjectPersistenceStructureTest {

    @Test
    void projectPersistenceUsesTheRequiredLayerPackagesAndSpringDataJpa() {
        assertTrue(ProjectEntity.class.getPackageName().startsWith("com.lab.labtimesheet.feature.project.model.entity"));
        assertTrue(ProjectService.class.getPackageName().startsWith("com.lab.labtimesheet.feature.project.service"));
        assertTrue(JpaRepository.class.isAssignableFrom(ProjectRepository.class));
        assertTrue(ProjectRepository.class.getInterfaces().length > 0);
        assertTrue(ProjectEntity.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertFalse(Arrays.stream(ProjectService.class.getDeclaredFields())
                .map(field -> field.getType())
                .anyMatch(JdbcOperations.class::isAssignableFrom));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.lab.labtimesheet.feature.project.model.entity.ProjectUserEntity"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.lab.labtimesheet.feature.project.model.entity.ProjectTaskEntity"));
    }
}
