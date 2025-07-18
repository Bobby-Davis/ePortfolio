package com.animals.rescue.controller;

import com.animals.rescue.model.Animal;
import com.animals.rescue.service.AnimalService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;


@Validated
@RestController
@RequestMapping("/api/animals")
@CrossOrigin(origins = "*")  // Allow calls from angular frontend
public class AnimalController {
    

    @Autowired
    private AnimalService animalService;

    @Autowired
    private MongoTemplate mongoTemplate;

    // Get all animals
    @GetMapping
    public List<Animal> getAllAnimals() {
        return animalService.getAllAnimals();
    }

    // Get animal by ID
    @GetMapping("/{id}")
    public Optional<Animal> getAnimalById(@PathVariable String id) {
        return animalService.getAnimalById(id);
    }


    // Create new animal
    @PostMapping 
    public Animal createAnimal(@Valid @RequestBody Animal animal) {
        return animalService.createAnimal(animal);
    }

    // Update animal
    @PutMapping("/{id}")
    public Animal updateAnimal(@PathVariable String id, @Valid @RequestBody Animal animal) {
        return animalService.updateAnimal(id, animal);
    }

    // Delete animal
    @DeleteMapping("/{id}")
    public void deleteAnimal(@PathVariable String id) {
        animalService.deleteAnimal(id);
    }

    // Get available animals by type
    @GetMapping("/available")
    public List<Animal> getAvailaAnimals(@RequestParam String type) {
        return animalService.getAvailableAnimals(type);
    }

    // Get animals matching filters
    @GetMapping("/filter")
    public Map<String, Object> filterAnimals(
        @RequestParam(required = false) String types,
        @RequestParam(required = false) String sizes,
        @RequestParam(required = false) Integer ageMin,
        @RequestParam(required = false) Integer ageMax,
        @RequestParam(required = false) Boolean available,
        @RequestParam(required = false) String sortBy,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "6") int pageSize
    ) {
        Query query = new Query();

        // Filter by animal types
        if (types != null) {
            List<String> typeList = Arrays.asList(types.split(","));
            query.addCriteria(Criteria.where("type").in(typeList));
        }

        // Filter by sizes (weight range)
        if (sizes != null) {
            List<String> sizeList = Arrays.asList(sizes.split(","));
            List<Criteria> sizeCriterias = new ArrayList<>();
            for (String size : sizeList) {
                switch (size) {
                    case "small" -> sizeCriterias.add(Criteria.where("weight").lt(25));
                    case "medium" -> sizeCriterias.add(Criteria.where("weight").gte(25).lte(50));
                    case "large" -> sizeCriterias.add(Criteria.where("weight").gt(50));
                }
            }
            // Combine size filters
            if (!sizeCriterias.isEmpty()) {
                query.addCriteria(new Criteria().orOperator(sizeCriterias.toArray(new Criteria[0])));
            }
        }

        // If min or max age is provided, build an age filter
        if (ageMin != null || ageMax != null) {
            Criteria ageCriteria = Criteria.where("age");

            // If both min and max age are set, filter animals within that range
            if (ageMin != null && ageMax != null) {
                ageCriteria.gte(ageMin).lte(ageMax);

            // if only min age is set, filter animals with age greater or equal to min
            } else if (ageMin != null) {
                ageCriteria.gte(ageMin);

            // If only max age is set, filter animals with age less than or equal to max
            } else if (ageMax != null) {
                ageCriteria.lte(ageMax);
            }
            query.addCriteria(ageCriteria);
        }

        // If "available" is true, filter animals that are not reserved and are in service
        if (available != null && available) {
            query.addCriteria(Criteria.where("reserved").is(false));
            query.addCriteria(Criteria.where("trainingStatus").is("In Service"));
        }

        if (sortBy != null) {
            switch (sortBy) {
                case "sizeAsc" -> query.with(Sort.by(Sort.Direction.ASC, "weight"));
                case "sizeDesc" -> query.with(Sort.by(Sort.Direction.DESC, "weight"));
                case "nameAsc" -> query.with(Sort.by(Sort.Direction.ASC, "name"));
                case "nameDesc" -> query.with(Sort.by(Sort.Direction.DESC, "name"));
                case "ageAsc" -> query.with(Sort.by(Sort.Direction.ASC, "age"));
                case "ageDesc" -> query.with(Sort.by(Sort.Direction.DESC, "age"));
            }
        }

        // Count total number of matching animals before applying pagination
        long total = mongoTemplate.count(query, Animal.class);

        // Apply Pagination
        query.with(PageRequest.of(page, pageSize));

        // Execute the query with filters and pagination
        List<Animal> animals = mongoTemplate.find(query, Animal.class);

        // Prepare the response map containing bot the filtered data and total count
        Map<String, Object> response = new HashMap<>();
        response.put("content", animals);       // actual list of animals for the current page
        response.put("totalElements", total);   // Total number of matching animals before pagination

        // return both paginated animal data and total match count
        return response;
    }
}
