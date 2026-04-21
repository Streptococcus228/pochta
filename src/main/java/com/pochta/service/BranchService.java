package com.pochta.service;

import com.pochta.model.Branch;
import com.pochta.repository.BranchRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @PostConstruct
    public void init() {
        // Оновлюємо назви відділень, якщо вони застарілі або на російській
        if (branchRepository.count() > 0) {
            List<Branch> current = branchRepository.findAll();
            boolean needUpdate = current.stream().anyMatch(b -> 
                b.getName().equals("Одесса-Центр") || b.getName().equals("Киев-Главпочтамт") || 
                b.getName().equals("Львов-1") || b.getName().equals("Харьков-Север") ||
                b.getName().equals("Днепр-Юг")
            );
            if (needUpdate) {
                branchRepository.deleteAll();
            }
        }

        if (branchRepository.count() == 0) {
            branchRepository.saveAll(List.of(
                new Branch(null, "Одеса-Центр", 0, 46.4825, 30.7233),
                new Branch(null, "Київ-Головпоштамт", 480, 50.4501, 30.5234),
                new Branch(null, "Львів-1", 350, 49.8397, 24.0297),
                new Branch(null, "Харків-Північ", 700, 49.9808, 36.2527),
                new Branch(null, "Дніпро-Південь", 400, 48.4647, 35.0462)
            ));
        }
    }

    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }

    public void addBranch(String name, int distance, double latitude, double longitude) {
        Branch branch = Branch.builder()
                .name(name)
                .distance(distance)
                .latitude(latitude)
                .longitude(longitude)
                .build();
        branchRepository.save(branch);
    }

    public Branch getBranchByName(String name) {
        return branchRepository.findByName(name).orElse(null);
    }
}
