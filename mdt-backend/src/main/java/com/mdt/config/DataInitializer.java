package com.mdt.config;

import com.mdt.entity.Department;
import com.mdt.entity.Expert;
import com.mdt.entity.ExpertStatus;
import com.mdt.repository.DepartmentRepository;
import com.mdt.repository.ExpertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(DepartmentRepository departmentRepository,
                                      ExpertRepository expertRepository) {
        return args -> {
            if (departmentRepository.count() == 0) {
                log.info("初始化科室数据...");

                Department internalDept = new Department();
                internalDept.setCode("INTERNAL");
                internalDept.setName("内科");
                internalDept.setDescription("内科专家团队");
                internalDept = departmentRepository.save(internalDept);

                Department surgeryDept = new Department();
                surgeryDept.setCode("SURGERY");
                surgeryDept.setName("外科");
                surgeryDept.setDescription("外科专家团队");
                surgeryDept = departmentRepository.save(surgeryDept);

                Department imagingDept = new Department();
                imagingDept.setCode("IMAGING");
                imagingDept.setName("影像科");
                imagingDept.setDescription("影像科专家团队");
                imagingDept = departmentRepository.save(imagingDept);

                Department oncologyDept = new Department();
                oncologyDept.setCode("ONCOLOGY");
                oncologyDept.setName("肿瘤科");
                oncologyDept.setDescription("肿瘤科专家团队");
                oncologyDept = departmentRepository.save(oncologyDept);

                Department pathologyDept = new Department();
                pathologyDept.setCode("PATHOLOGY");
                pathologyDept.setName("病理科");
                pathologyDept.setDescription("病理科专家团队");
                pathologyDept = departmentRepository.save(pathologyDept);

                log.info("初始化专家数据...");

                List<Expert> internalExperts = Arrays.asList(
                        createExpert("doctor_li", "李明华", internalDept.getId(), "主任医师", "心血管疾病", ExpertStatus.ONLINE),
                        createExpert("doctor_wang", "王建国", internalDept.getId(), "副主任医师", "呼吸系统疾病", ExpertStatus.ONLINE),
                        createExpert("doctor_zhang", "张秀英", internalDept.getId(), "主治医师", "消化系统疾病", ExpertStatus.OFFLINE)
                );
                expertRepository.saveAll(internalExperts);

                List<Expert> surgeryExperts = Arrays.asList(
                        createExpert("doctor_chen", "陈志强", surgeryDept.getId(), "主任医师", "普外科手术", ExpertStatus.ONLINE),
                        createExpert("doctor_liu", "刘伟", surgeryDept.getId(), "副主任医师", "微创手术", ExpertStatus.BUSY),
                        createExpert("doctor_zhao", "赵芳", surgeryDept.getId(), "主治医师", "肝胆外科", ExpertStatus.ONLINE)
                );
                expertRepository.saveAll(surgeryExperts);

                List<Expert> imagingExperts = Arrays.asList(
                        createExpert("doctor_sun", "孙晓明", imagingDept.getId(), "主任医师", "CT/MRI诊断", ExpertStatus.ONLINE),
                        createExpert("doctor_zhou", "周丽", imagingDept.getId(), "副主任医师", "放射诊断", ExpertStatus.ONLINE)
                );
                expertRepository.saveAll(imagingExperts);

                List<Expert> oncologyExperts = Arrays.asList(
                        createExpert("doctor_wu", "吴学军", oncologyDept.getId(), "主任医师", "肿瘤化疗", ExpertStatus.ONLINE),
                        createExpert("doctor_zheng", "郑红梅", oncologyDept.getId(), "副主任医师", "肿瘤靶向治疗", ExpertStatus.ONLINE)
                );
                expertRepository.saveAll(oncologyExperts);

                List<Expert> pathologyExperts = Arrays.asList(
                        createExpert("doctor_huang", "黄志远", pathologyDept.getId(), "主任医师", "病理诊断", ExpertStatus.ONLINE)
                );
                expertRepository.saveAll(pathologyExperts);

                log.info("数据初始化完成");
            }
        };
    }

    private Expert createExpert(String username, String name, Long deptId,
                                String title, String specialty, String status) {
        Expert expert = new Expert();
        expert.setUsername(username);
        expert.setName(name);
        expert.setDepartmentId(deptId);
        expert.setTitle(title);
        expert.setSpecialty(specialty);
        expert.setStatus(status);
        return expert;
    }
}
