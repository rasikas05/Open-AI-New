package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.FunctionMaster;
import com.ai.openai_api_service.repository.FunctionMasterRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FunctionDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FunctionDataLoader.class);

    private final FunctionMasterRepository repository;
    private final ObjectMapper objectMapper;

    public FunctionDataLoader(FunctionMasterRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        log.info("Starting FunctionDataLoader");

        ClassPathResource resource = new ClassPathResource("functions.json");
        if (!resource.exists()) {
            log.info("functions.json not found in classpath. Skipping FunctionDataLoader.");
            return;
        }

        try {
            String json = new String(resource.getInputStream().readAllBytes());
            log.info("functions.json loaded successfully");
            log.info("JSON length: {}", json.length());

            System.out.println("===== JSON START =====");
            System.out.println(json.substring(0, Math.min(json.length(), 1000)));
            System.out.println("===== JSON END =====");

            FunctionsFile functionsFile = objectMapper.readValue(json, FunctionsFile.class);
            int resultCount = functionsFile.getResults() == null ? 0 : functionsFile.getResults().size();
            List<FunctionMasterJson> records = new ArrayList<>();
            if (functionsFile.getResults() != null) {
                for (ResultNode resultNode : functionsFile.getResults()) {
                    if (resultNode.getRecords() != null) {
                        records.addAll(resultNode.getRecords());
                    }
                }
            }
            log.info("Results found: {}", resultCount);
            log.info("Records found: {}", records.size());
            if (records.isEmpty()) {
                log.info("No records found in functions.json. Nothing to load.");
                return;
            }

            List<FunctionMaster> existingRecords = repository.findAll();
            Set<String> existingKeys = existingRecords.stream()
                    .map(record -> buildUniqueKey(record.getMnid(), record.getMnvr(), record.getFnid()))
                    .collect(Collectors.toCollection(HashSet::new));

            Set<String> seenKeys = new HashSet<>();
            List<FunctionMaster> toSave = new ArrayList<>();
            int skippedDuplicates = 0;
            int skippedInvalid = 0;

            for (FunctionMasterJson jsonRecord : records) {
                if (jsonRecord.MNID == null || jsonRecord.MNVR == null || jsonRecord.FNID == null) {
                    skippedInvalid++;
                    log.warn("Skipping function record with missing required key fields: {}", jsonRecord);
                    continue;
                }

                String uniqueKey = buildUniqueKey(jsonRecord.MNID, jsonRecord.MNVR, jsonRecord.FNID);
                if (existingKeys.contains(uniqueKey) || seenKeys.contains(uniqueKey)) {
                    skippedDuplicates++;
                    continue;
                }

                seenKeys.add(uniqueKey);
                existingKeys.add(uniqueKey);

                FunctionMaster entity = FunctionMaster.builder()
                        .mnid(jsonRecord.MNID)
                        .mnvr(jsonRecord.MNVR)
                        .lmts(jsonRecord.LMTS)
                        .levl(jsonRecord.LEVL)
                        .fnid(jsonRecord.FNID)
                        .fnt3(jsonRecord.FNT3)
                        .tx40(jsonRecord.TX40)
                        .msid(jsonRecord.MSID)
                        .mnop(jsonRecord.MNOP)
                        .mash(jsonRecord.MASH)
                        .maon(jsonRecord.MAON)
                        .mdev(jsonRecord.MDEV)
                        .urla(jsonRecord.URLA)
                        .pame(jsonRecord.PAME)
                        .build();

                toSave.add(entity);
            }

            if (toSave.isEmpty()) {
                log.info("No new FunctionMaster records to insert. Skipped duplicates: {}, invalid records: {}.", skippedDuplicates, skippedInvalid);
                return;
            }

            List<FunctionMaster> saved = repository.saveAll(toSave);
            String resultMessage = String.format("Inserted %d FunctionMaster records. Skipped duplicates: %d, invalid records: %d.",
                    saved.size(), skippedDuplicates, skippedInvalid);

            log.info(resultMessage);
            System.out.println(resultMessage);

        } catch (IOException ioException) {
            log.error("Unable to read functions.json from classpath.", ioException);
        } catch (DataAccessException daoException) {
            log.error("Database error while loading FunctionMaster records.", daoException);
        } catch (Exception exception) {
            log.error("Unexpected error occurred while loading FunctionMaster data.", exception);
        }
    }

    private static String buildUniqueKey(String mnid, String mnvr, String fnid) {
        return String.join("|", mnid == null ? "" : mnid, mnvr == null ? "" : mnvr, fnid == null ? "" : fnid);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FunctionsFile {
        public List<ResultNode> results = new ArrayList<>();

        public List<ResultNode> getResults() {
            return results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ResultNode {
        public String transaction;
        public List<FunctionMasterJson> records = new ArrayList<>();

        public String getTransaction() {
            return transaction;
        }

        public List<FunctionMasterJson> getRecords() {
            return records;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FunctionMasterJson {
        public String MNID;
        public String MNVR;
        public Long LMTS;
        public Integer LEVL;
        public String FNID;
        public String FNT3;
        public String TX40;
        public String MSID;
        public String MNOP;
        public String MASH;
        public String MAON;
        public String MDEV;
        public String URLA;
        public String PAME;

        @Override
        public String toString() {
            return "FunctionMasterJson{" +
                    "MNID='" + MNID + '\'' +
                    ", MNVR='" + MNVR + '\'' +
                    ", FNID='" + FNID + '\'' +
                    '}';
        }
    }
}
