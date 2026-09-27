package com.school.admission.web;

import com.school.admission.security.CurrentUser;
import com.school.admission.service.ErpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/erp")
@Tag(name="School ERP", description="Student lifecycle, academics, fees, attendance, transport and school operations")
public class ErpController {
    private final ErpService erp; private final CurrentUser currentUser;
    public ErpController(ErpService erp, CurrentUser currentUser){this.erp=erp;this.currentUser=currentUser;}

    @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    Map<String,Object> dashboard(@RequestParam String academicYear){return erp.managementDashboard(academicYear);}

    @GetMapping("/students") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','ACCOUNTS','FRONT_OFFICE','GUARDIAN')")
    List<Map<String,Object>> students(@RequestParam String academicYear,@RequestParam(required=false) String classCode,Authentication a){return erp.students(academicYear,classCode,isGuardian(a)?uid(a):null);}
    @PostMapping("/students") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> createStudent(@RequestBody Map<String,Object> body){return erp.createStudent(body);}
    @GetMapping("/students/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','ACCOUNTS','FRONT_OFFICE','GUARDIAN')")
    Map<String,Object> student(@PathVariable Long id, Authentication a){
        var row=erp.student(id);
        if(isGuardian(a) && !erp.students(String.valueOf(row.get("academicYear")),null,uid(a)).stream().anyMatch(s->Objects.equals(s.get("id"),id))) throw new org.springframework.security.access.AccessDeniedException("Student is not linked to this guardian");
        return row;
    }
    @PutMapping("/students/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> updateStudent(@PathVariable Long id,@RequestBody Map<String,Object> body){return erp.updateStudent(id,body);}
    @PatchMapping("/students/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> updateStudentStatus(@PathVariable Long id,@RequestBody Map<String,Object> body){return erp.updateStudentStatus(id,String.valueOf(body.get("status")));}
    @DeleteMapping("/students/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void deleteStudent(@PathVariable Long id){erp.deleteStudent(id);}

    @GetMapping("/attendance") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','GUARDIAN')")
    Map<String,Object> attendance(@RequestParam String academicYear,@RequestParam(required=false) String classCode,@RequestParam(required=false) LocalDate date,Authentication a){return erp.attendance(academicYear,classCode,date==null?LocalDate.now():date,isGuardian(a)?uid(a):null);}
    @PostMapping("/attendance") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER')")
    void saveAttendance(@RequestBody Map<String,Object> body,Authentication a){LocalDate d=LocalDate.parse(String.valueOf(body.get("date")));@SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)body.getOrDefault("rows",List.of());erp.saveAttendance(d,rows,uid(a));}

    @GetMapping("/fees") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS','GUARDIAN')")
    Map<String,Object> fees(@RequestParam String academicYear,@RequestParam(required=false) String classCode,Authentication a){return erp.fees(academicYear,classCode,isGuardian(a)?uid(a):null);}
    @PostMapping("/fees") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    Map<String,Object> createFee(@RequestBody Map<String,Object> body){return erp.createFeeDue(body);}
    @PutMapping("/fees/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    Map<String,Object> updateFee(@PathVariable Long id,@RequestBody Map<String,Object> body){return erp.updateFeeDue(id,body);}
    @PostMapping("/fees/{id}/waive") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    Map<String,Object> waiveFee(@PathVariable Long id){return erp.waiveFee(id);}
    @DeleteMapping("/fees/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    void deleteFee(@PathVariable Long id){erp.deleteFeeDue(id);}
    @PostMapping("/fees/{id}/payment") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    void feePayment(@PathVariable Long id,@RequestBody Map<String,Object> body){erp.recordFeePayment(id,new BigDecimal(String.valueOf(body.get("amount"))),String.valueOf(body.getOrDefault("mode","CASH")),String.valueOf(body.getOrDefault("reference","")));}

    @GetMapping("/timetable") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','GUARDIAN')")
    List<Map<String,Object>> timetable(@RequestParam String academicYear,@RequestParam(required=false) String classCode,Authentication a){Long teacher=isTeacher(a)&&!isManagement(a)?uid(a):null;if(isGuardian(a)&&classCode==null){var ss=erp.students(academicYear,null,uid(a));classCode=ss.isEmpty()?null:String.valueOf(ss.get(0).get("classCode"));}return erp.timetable(academicYear,classCode,teacher);}
    @PostMapping("/timetable") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void saveTimetable(@RequestBody Map<String,Object> body){@SuppressWarnings("unchecked") List<Map<String,Object>> entries=(List<Map<String,Object>>)body.getOrDefault("entries",List.of());erp.saveTimetable(String.valueOf(body.get("academicYear")),String.valueOf(body.get("classCode")),entries);}
    @GetMapping("/teachers") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    List<Map<String,Object>> teachers(){return erp.teachers();}

    @GetMapping("/exams") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','GUARDIAN')")
    List<Map<String,Object>> exams(@RequestParam String academicYear){return erp.exams(academicYear);}
    @PostMapping("/exams") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    Map<String,Object> createExam(@RequestBody Map<String,Object> body){return erp.createExam(body);}
    @PutMapping("/exams/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    Map<String,Object> updateExam(@PathVariable Long id,@RequestBody Map<String,Object> body){return erp.updateExam(id,body);}
    @PostMapping("/exams/{id}/subjects") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    Map<String,Object> saveExamSubjects(@PathVariable Long id,@RequestBody Map<String,Object> body){@SuppressWarnings("unchecked") List<Map<String,Object>> subjects=(List<Map<String,Object>>)body.getOrDefault("subjects",List.of());return erp.saveExamSubjects(id,String.valueOf(body.get("academicYear")),String.valueOf(body.get("classCode")),subjects);}
    @DeleteMapping("/exam-subjects/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void deleteExamSubject(@PathVariable Long id){erp.deleteExamSubject(id);}
    @GetMapping("/marks") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','GUARDIAN')")
    Map<String,Object> marks(@RequestParam Long examId,@RequestParam String academicYear,@RequestParam String classCode,Authentication a){return erp.marks(examId,academicYear,classCode,isGuardian(a)?uid(a):null);}
    @PostMapping("/marks") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER')")
    void saveMarks(@RequestBody Map<String,Object> body,Authentication a){@SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)body.getOrDefault("rows",List.of());erp.saveMarks(rows,uid(a));}

    @GetMapping("/report-cards") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','GUARDIAN')")
    List<Map<String,Object>> reportCards(@RequestParam String academicYear,Authentication a){return erp.reportCards(academicYear,isGuardian(a)?uid(a):null);}

    @GetMapping("/transport") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','GUARDIAN')")
    List<Map<String,Object>> transport(Authentication a){return erp.transport(isGuardian(a)?uid(a):null);}
    @PostMapping("/transport/routes") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> createTransportRoute(@RequestBody Map<String,Object> body){return erp.createTransportRoute(body);}
    @PutMapping("/transport/routes/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    void updateTransportRoute(@PathVariable Long id,@RequestBody Map<String,Object> body){erp.updateTransportRoute(id,body);}
    @DeleteMapping("/transport/routes/{id}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void deleteTransportRoute(@PathVariable Long id){erp.deleteTransportRoute(id);}
    @PostMapping("/transport/routes/{routeId}/stops") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> createTransportStop(@PathVariable Long routeId,@RequestBody Map<String,Object> body){return erp.saveTransportStop(routeId,null,body);}
    @PutMapping("/transport/routes/{routeId}/stops/{stopId}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    Map<String,Object> updateTransportStop(@PathVariable Long routeId,@PathVariable Long stopId,@RequestBody Map<String,Object> body){return erp.saveTransportStop(routeId,stopId,body);}
    @DeleteMapping("/transport/routes/{routeId}/stops/{stopId}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void deleteTransportStop(@PathVariable Long routeId,@PathVariable Long stopId){erp.deleteTransportStop(routeId,stopId);}
    @PostMapping("/transport/allocations") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    void assignTransport(@RequestBody Map<String,Object> body){erp.assignStudentTransport(body);}
    @DeleteMapping("/transport/allocations/student/{studentId}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    void unassignTransport(@PathVariable Long studentId){erp.unassignStudentTransport(studentId);}

    @GetMapping("/users") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    List<Map<String,Object>> users(){return erp.users();}

    @GetMapping("/notifications") @PreAuthorize("isAuthenticated()")
    List<Map<String,Object>> notifications(Authentication a){return erp.notifications(uid(a),isManagement(a));}
    @PostMapping("/notifications") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void createNotification(@RequestBody Map<String,Object> body){erp.createNotification(Long.valueOf(String.valueOf(body.get("userId"))),String.valueOf(body.get("title")),String.valueOf(body.get("body")),String.valueOf(body.getOrDefault("category","GENERAL")));}
    @PostMapping("/notifications/{id}/read") @PreAuthorize("isAuthenticated()")
    void read(@PathVariable Long id,Authentication a){erp.markNotificationRead(id,uid(a));}
    @PostMapping("/notifications/read-all") @PreAuthorize("isAuthenticated()")
    void readAll(Authentication a){erp.markAllNotificationsRead(uid(a));}

    @GetMapping("/certificates") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','GUARDIAN')")
    List<Map<String,Object>> certificates(Authentication a){return erp.certificates(isGuardian(a)?uid(a):null);}
    @PostMapping("/certificates") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    void issue(@RequestBody Map<String,Object> body,Authentication a){erp.issueCertificate(Long.valueOf(String.valueOf(body.get("studentId"))),String.valueOf(body.get("type")),String.valueOf(body.get("title")),String.valueOf(body.get("body")),uid(a));}

    @GetMapping(value="/export/students",produces="text/csv") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','TEACHER','FRONT_OFFICE')")
    ResponseEntity<String> studentsCsv(@RequestParam String academicYear){return download("student-master-"+academicYear+".csv",erp.studentsCsv(academicYear));}
    @GetMapping(value="/export/fees",produces="text/csv") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','ACCOUNTS')")
    ResponseEntity<String> feesCsv(@RequestParam String academicYear){return download("fee-register-"+academicYear+".csv",erp.feesCsv(academicYear));}

    private ResponseEntity<String> download(String filename,String data){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(data);}
    private Long uid(Authentication a){return currentUser.user(a).getId();}
    private boolean isGuardian(Authentication a){return currentUser.roles(a).contains("GUARDIAN");}
    private boolean isTeacher(Authentication a){return currentUser.roles(a).contains("TEACHER");}
    private boolean isManagement(Authentication a){return currentUser.roles(a).stream().anyMatch(r->Set.of("ADMIN","PRINCIPAL").contains(r));}
}
