package com.school.admission.service;

import com.school.admission.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Service
public class ErpService {
    private final JdbcTemplate jdbc;

    public ErpService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> managementDashboard(String academicYear) {
        Long yearId = yearId(academicYear);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("students", count("select count(*) from student where academic_year_id=? and status='ACTIVE'", yearId));
        m.put("classes", count("select count(*) from class_config where academic_year_id=?", yearId));
        m.put("presentToday", count("select count(*) from attendance_record ar join student s on s.id=ar.student_id where s.academic_year_id=? and ar.attendance_on=current_date and ar.status in ('PRESENT','LATE')", yearId));
        long markedToday = count("select count(*) from attendance_record ar join student s on s.id=ar.student_id where s.academic_year_id=? and ar.attendance_on=current_date", yearId);
        long active = ((Number)m.get("students")).longValue();
        m.put("attendanceRate", markedToday == 0 ? 0 : round(count("select count(*) from attendance_record ar join student s on s.id=ar.student_id where s.academic_year_id=? and ar.attendance_on=current_date and ar.status in ('PRESENT','LATE')", yearId) * 100.0 / markedToday));
        BigDecimal totalDue = money("select coalesce(sum(amount),0) from student_fee_due fd join student s on s.id=fd.student_id where s.academic_year_id=?", yearId);
        BigDecimal totalPaid = money("select coalesce(sum(paid_amount),0) from student_fee_due fd join student s on s.id=fd.student_id where s.academic_year_id=?", yearId);
        m.put("feeBilled", totalDue); m.put("feeCollected", totalPaid); m.put("feeOutstanding", totalDue.subtract(totalPaid));
        m.put("feeCollectionRate", totalDue.signum()==0 ? 0 : round(totalPaid.doubleValue()*100/totalDue.doubleValue()));
        m.put("overdueFeeItems", count("select count(*) from student_fee_due fd join student s on s.id=fd.student_id where s.academic_year_id=? and fd.status in ('OVERDUE','PARTIAL') and fd.due_date < current_date", yearId));
        m.put("transportStudents", count("select count(*) from student_transport st join student s on s.id=st.student_id where s.academic_year_id=? and st.active=true", yearId));
        m.put("publishedExams", count("select count(*) from exam where academic_year_id=? and status='PUBLISHED'", yearId));
        m.put("activeStudents", active);
        m.put("classStrength", classStrength(academicYear));
        m.put("attendanceTrend", attendanceTrend(yearId));
        m.put("feeByClass", feeByClass(yearId));
        return m;
    }

    public List<Map<String,Object>> classStrength(String academicYear) {
        Long yearId = yearId(academicYear);
        return jdbc.query("""
            select c.class_code, c.display_name, c.seats_total, count(s.id) students
            from class_config c left join student s on s.class_config_id=c.id and s.status='ACTIVE'
            where c.academic_year_id=? group by c.id,c.class_code,c.display_name,c.seats_total order by c.id
            """, (rs,i) -> row("classCode",rs.getString(1),"displayName",rs.getString(2),"capacity",rs.getInt(3),"students",rs.getInt(4)), yearId);
    }

    private List<Map<String,Object>> attendanceTrend(Long yearId) {
        return jdbc.query("""
            select ar.attendance_on, count(*) total,
                   sum(case when ar.status in ('PRESENT','LATE') then 1 else 0 end) present
            from attendance_record ar join student s on s.id=ar.student_id
            where s.academic_year_id=? and ar.attendance_on >= dateadd(day,-14,current_date) and ar.status is not null
            group by ar.attendance_on order by ar.attendance_on
            """, (rs,i) -> {
                int t=rs.getInt("total"), p=rs.getInt("present");
                return row("date",rs.getDate("attendance_on").toLocalDate().toString(),"rate", t==0?0:round(p*100.0/t));
            }, yearId);
    }

    private List<Map<String,Object>> feeByClass(Long yearId) {
        return jdbc.query("""
            select c.class_code,c.display_name,coalesce(sum(fd.amount),0) billed,coalesce(sum(fd.paid_amount),0) paid
            from class_config c left join student s on s.class_config_id=c.id left join student_fee_due fd on fd.student_id=s.id
            where c.academic_year_id=? group by c.id,c.class_code,c.display_name order by c.id
            """, (rs,i)->row("classCode",rs.getString(1),"displayName",rs.getString(2),"billed",rs.getBigDecimal(3),"paid",rs.getBigDecimal(4)), yearId);
    }

    public List<Map<String,Object>> students(String academicYear, String classCode, Long guardianUserId) {
        Long yearId = yearId(academicYear);
        StringBuilder sql = new StringBuilder("""
            select s.id,s.admission_no,s.roll_no,s.full_name,s.dob,s.gender,s.blood_group,s.house_name,s.guardian_name,s.guardian_phone,s.guardian_email,s.guardian_email_verified_at,s.guardian_phone_verified_at,s.address_line,s.status,s.joined_on,c.class_code,c.display_name
            from student s join class_config c on c.id=s.class_config_id where s.academic_year_id=?
            """);
        List<Object> args = new ArrayList<>(); args.add(yearId);
        if (classCode != null && !classCode.isBlank()) { sql.append(" and c.class_code=?"); args.add(normalizeClassCode(classCode)); }
        if (guardianUserId != null) { sql.append(" and s.guardian_user_id=?"); args.add(guardianUserId); }
        sql.append(" order by c.id,s.roll_no,s.full_name");
        return jdbc.query(sql.toString(), (rs,i)->row(
            "id",rs.getLong("id"),"admissionNo",rs.getString("admission_no"),"rollNo",rs.getString("roll_no"),
            "fullName",rs.getString("full_name"),"dob",date(rs.getDate("dob")),"gender",rs.getString("gender"),
            "bloodGroup",rs.getString("blood_group"),"house",rs.getString("house_name"),"guardianName",rs.getString("guardian_name"),
            "guardianPhone",rs.getString("guardian_phone"),"guardianEmail",rs.getString("guardian_email"),"emailVerified",rs.getTimestamp("guardian_email_verified_at")!=null,"phoneVerified",rs.getTimestamp("guardian_phone_verified_at")!=null,"address",rs.getString("address_line"),
            "status",rs.getString("status"),"joinedOn",date(rs.getDate("joined_on")),"classCode",rs.getString("class_code"),"className",rs.getString("display_name")
        ), args.toArray());
    }

    @Transactional
    public Map<String,Object> createStudent(Map<String,Object> body) {
        String year = str(body.get("academicYear")); String classCode=str(body.get("classCode"));
        Long yearId=yearId(year); Long classId=classId(yearId,classCode);
        if (classId == null) {
            throw new IllegalArgumentException("Class '" + classCode + "' is not configured for academic year '" + year + "'");
        }
        String admissionNo = optional(body,"admissionNo","ADM"+String.valueOf(System.currentTimeMillis()).substring(5));
        jdbc.update("""
            insert into student(admission_no,roll_no,academic_year_id,class_config_id,full_name,dob,gender,blood_group,house_name,guardian_name,guardian_phone,guardian_email,address_line,status,joined_on)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',current_date)
            """, admissionNo, str(body.get("rollNo")), yearId,classId,str(body.get("fullName")), sqlDate(body.get("dob")),str(body.get("gender")),str(body.get("bloodGroup")),str(body.get("house")),str(body.get("guardianName")),str(body.get("guardianPhone")),str(body.get("guardianEmail")),str(body.get("address")));
        Long id=jdbc.queryForObject("select id from student where admission_no=?",Long.class,admissionNo);
        return students(year,classCode,null).stream().filter(x->Objects.equals(x.get("id"),id)).findFirst().orElse(row("id",id));
    }

    public Map<String,Object> student(Long id) {
        return jdbc.query("""
            select s.id,s.admission_no,s.roll_no,s.full_name,s.dob,s.gender,s.blood_group,s.house_name,s.guardian_name,s.guardian_phone,s.guardian_email,s.guardian_email_verified_at,s.guardian_phone_verified_at,s.address_line,s.status,s.joined_on,c.class_code,c.display_name,ay.label academic_year
            from student s join class_config c on c.id=s.class_config_id join academic_year ay on ay.id=s.academic_year_id
            where s.id=?
            """, (rs,i)->row(
                "id",rs.getLong("id"),"admissionNo",rs.getString("admission_no"),"rollNo",rs.getString("roll_no"),
                "fullName",rs.getString("full_name"),"dob",date(rs.getDate("dob")),"gender",rs.getString("gender"),
                "bloodGroup",rs.getString("blood_group"),"house",rs.getString("house_name"),"guardianName",rs.getString("guardian_name"),
                "guardianPhone",rs.getString("guardian_phone"),"guardianEmail",rs.getString("guardian_email"),"emailVerified",rs.getTimestamp("guardian_email_verified_at")!=null,"phoneVerified",rs.getTimestamp("guardian_phone_verified_at")!=null,"address",rs.getString("address_line"),
                "status",rs.getString("status"),"joinedOn",date(rs.getDate("joined_on")),"classCode",rs.getString("class_code"),
                "className",rs.getString("display_name"),"academicYear",rs.getString("academic_year")
            ), id).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Student not found: "+id));
    }

    @Transactional
    public Map<String,Object> updateStudent(Long id, Map<String,Object> body) {
        Map<String,Object> current=student(id);
        String year=optional(body,"academicYear",String.valueOf(current.get("academicYear")));
        String classCode=optional(body,"classCode",String.valueOf(current.get("classCode")));
        Long yearId=yearId(year); Long classId=classId(yearId,classCode);
        if(classId==null) throw new IllegalArgumentException("Class '"+classCode+"' is not configured for academic year '"+year+"'");
        String admissionNo=optional(body,"admissionNo",String.valueOf(current.get("admissionNo")));
        String status=optional(body,"status",String.valueOf(current.get("status"))).toUpperCase(Locale.ROOT);
        if(!Set.of("ACTIVE","INACTIVE","ALUMNI","WITHDRAWN").contains(status)) throw new IllegalArgumentException("Unsupported student status: "+status);
        jdbc.update("""
            update student set admission_no=?,roll_no=?,academic_year_id=?,class_config_id=?,full_name=?,dob=?,gender=?,blood_group=?,house_name=?,guardian_name=?,guardian_phone=?,guardian_email=?,guardian_phone_verified_at=case when guardian_phone=? then guardian_phone_verified_at else null end,guardian_email_verified_at=case when guardian_email=? then guardian_email_verified_at else null end,address_line=?,status=?
            where id=?
            """, admissionNo, str(body.getOrDefault("rollNo",current.get("rollNo"))), yearId,classId,
            optional(body,"fullName",String.valueOf(current.get("fullName"))), sqlDate(body.containsKey("dob")?body.get("dob"):current.get("dob")),
            str(body.getOrDefault("gender",current.get("gender"))),str(body.getOrDefault("bloodGroup",current.get("bloodGroup"))),
            str(body.getOrDefault("house",current.get("house"))),str(body.getOrDefault("guardianName",current.get("guardianName"))),
            str(body.getOrDefault("guardianPhone",current.get("guardianPhone"))),str(body.getOrDefault("guardianEmail",current.get("guardianEmail"))),
            str(body.getOrDefault("guardianPhone",current.get("guardianPhone"))),str(body.getOrDefault("guardianEmail",current.get("guardianEmail"))),
            str(body.getOrDefault("address",current.get("address"))),status,id);
        return student(id);
    }

    @Transactional
    public Map<String,Object> updateStudentStatus(Long id, String status) {
        String normalized=status==null?"":status.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("ACTIVE","INACTIVE","ALUMNI","WITHDRAWN").contains(normalized)) throw new IllegalArgumentException("Unsupported student status: "+status);
        int changed=jdbc.update("update student set status=? where id=?",normalized,id);
        if(changed==0) throw new NoSuchElementException("Student not found: "+id);
        return student(id);
    }

    @Transactional
    public void deleteStudent(Long id) {
        int changed=jdbc.update("delete from student where id=?",id);
        if(changed==0) throw new NoSuchElementException("Student not found: "+id);
    }

    public Map<String,Object> attendance(String academicYear, String classCode, LocalDate day, Long guardianUserId) {
        Long yearId=yearId(academicYear);
        StringBuilder sql = new StringBuilder("""
            select s.id,s.roll_no,s.full_name,c.class_code,c.display_name,
                   ar.id attendance_id,ar.status attendance_status,ar.remarks,ar.marked_at
            from student s join class_config c on c.id=s.class_config_id
            left join attendance_record ar on ar.student_id=s.id and ar.attendance_on=?
            where s.academic_year_id=? and s.status='ACTIVE'
            """);
        List<Object> args=new ArrayList<>(); args.add(Date.valueOf(day)); args.add(yearId);
        String normalizedClassCode = normalizeClassCode(classCode);
        if(normalizedClassCode!=null&&!normalizedClassCode.isBlank()){sql.append(" and c.class_code=?");args.add(normalizedClassCode);}
        if(guardianUserId!=null){sql.append(" and s.guardian_user_id=?");args.add(guardianUserId);}
        sql.append(" order by c.id,s.roll_no,s.full_name");
        List<Map<String,Object>> rows=jdbc.query(sql.toString(),(rs,i)-> {
            String attendanceStatus = rs.getString("attendance_status");
            boolean isMarked = rs.getObject("attendance_id") != null && attendanceStatus != null && !attendanceStatus.isBlank();
            return row(
                "studentId",rs.getLong("id"),"rollNo",rs.getString("roll_no"),"studentName",rs.getString("full_name"),
                "classCode",rs.getString("class_code"),"className",rs.getString("display_name"),
                "marked",isMarked,"status",attendanceStatus,
                "remarks",rs.getString("remarks"),"markedAt",rs.getObject("marked_at") == null ? null : rs.getObject("marked_at").toString());
        },args.toArray());
        long marked=rows.stream().filter(r->Boolean.TRUE.equals(r.get("marked"))).count();
        long present=rows.stream().filter(r->"PRESENT".equals(r.get("status")) || "LATE".equals(r.get("status"))).count();
        long absent=rows.stream().filter(r->"ABSENT".equals(r.get("status"))).count();

        StringBuilder latestSql=new StringBuilder("""
            select max(ar.attendance_on)
            from attendance_record ar
            join student s on s.id=ar.student_id
            join class_config c on c.id=s.class_config_id
            where s.academic_year_id=? and s.status='ACTIVE' and ar.status is not null
            """);
        List<Object> latestArgs=new ArrayList<>(); latestArgs.add(yearId);
        if(normalizedClassCode!=null&&!normalizedClassCode.isBlank()){latestSql.append(" and c.class_code=?");latestArgs.add(normalizedClassCode);}
        if(guardianUserId!=null){latestSql.append(" and s.guardian_user_id=?");latestArgs.add(guardianUserId);}
        Date latest=jdbc.queryForObject(latestSql.toString(),Date.class,latestArgs.toArray());

        return row("date",day.toString(),"total",rows.size(),"marked",marked,"unmarked",rows.size()-marked,
                "present",present,"absent",absent,"rate",marked==0?0:round(present*100.0/marked),
                "latestSavedDate",date(latest),"rows",rows);
    }

    @Transactional
    public void saveAttendance(LocalDate day, List<Map<String,Object>> rows, Long markerId) {
        if(day==null) throw new IllegalArgumentException("Attendance date is required");
        if(rows==null) return;
        Set<String> allowedStatuses=Set.of("PRESENT","ABSENT","LATE","HALF_DAY");
        for(Map<String,Object> r:rows){
            Object studentIdValue=r.get("studentId");
            if(studentIdValue==null) throw new IllegalArgumentException("studentId is required for attendance");
            Long sid=Long.valueOf(String.valueOf(studentIdValue));
            String status=str(r.get("status"));

            // A blank status means the student has not been marked. Remove any legacy/null row
            // instead of persisting an attendance record whose status is NULL.
            if(status==null || status.isBlank()){
                jdbc.update("delete from attendance_record where student_id=? and attendance_on=?",sid,Date.valueOf(day));
                continue;
            }

            status=status.trim().toUpperCase(Locale.ROOT);
            if(!allowedStatuses.contains(status)) throw new IllegalArgumentException("Unsupported attendance status: "+status);
            int updated=jdbc.update("update attendance_record set status=?,remarks=?,marked_by_id=?,marked_at=current_timestamp where student_id=? and attendance_on=?",status,str(r.get("remarks")),markerId,sid,Date.valueOf(day));
            if(updated==0) jdbc.update("insert into attendance_record(student_id,attendance_on,status,remarks,marked_by_id) values(?,?,?,?,?)",sid,Date.valueOf(day),status,str(r.get("remarks")),markerId);
        }
    }

    public Map<String,Object> fees(String academicYear, String classCode, Long guardianUserId) {
        Long yearId=yearId(academicYear);
        String normalizedClassCode=normalizeClassCode(classCode);
        StringBuilder where=new StringBuilder(" where s.academic_year_id=?"); List<Object> args=new ArrayList<>(); args.add(yearId);
        if(normalizedClassCode!=null&&!normalizedClassCode.isBlank()){where.append(" and c.class_code=?");args.add(normalizedClassCode);}
        if(guardianUserId!=null){where.append(" and s.guardian_user_id=?");args.add(guardianUserId);}
        String sql="""
            select fd.id,s.id student_id,s.full_name,s.admission_no,c.class_code,c.display_name,fd.fee_type,fd.term_name,
                   fd.amount,fd.paid_amount,fd.due_date,fd.status,fd.paid_on,fd.payment_mode,fd.reference_no
            from student_fee_due fd join student s on s.id=fd.student_id join class_config c on c.id=s.class_config_id
            """+where+" order by fd.due_date,s.full_name,fd.id";
        List<Map<String,Object>> rows=jdbc.query(sql,(rs,i)->{
            BigDecimal amount=nvl(rs.getBigDecimal("amount"));
            BigDecimal paid=nvl(rs.getBigDecimal("paid_amount"));
            String status=rs.getString("status");
            BigDecimal outstanding="WAIVED".equalsIgnoreCase(status)?BigDecimal.ZERO:amount.subtract(paid).max(BigDecimal.ZERO);
            return row("id",rs.getLong("id"),"studentId",rs.getLong("student_id"),"studentName",rs.getString("full_name"),"admissionNo",rs.getString("admission_no"),"classCode",rs.getString("class_code"),"className",rs.getString("display_name"),"feeType",rs.getString("fee_type"),"term",rs.getString("term_name"),"amount",amount,"paid",paid,"outstanding",outstanding,"dueDate",date(rs.getDate("due_date")),"status",status==null?"DUE":status,"paidOn",date(rs.getDate("paid_on")),"paymentMode",rs.getString("payment_mode"),"referenceNo",rs.getString("reference_no"));
        },args.toArray());
        BigDecimal billed=rows.stream().filter(r->!"WAIVED".equalsIgnoreCase(String.valueOf(r.get("status")))).map(r->nvl((BigDecimal)r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal paid=rows.stream().map(r->nvl((BigDecimal)r.get("paid"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal outstanding=rows.stream().map(r->nvl((BigDecimal)r.get("outstanding"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        long overdue=rows.stream().filter(r->"OVERDUE".equalsIgnoreCase(String.valueOf(r.get("status")))||("PARTIAL".equalsIgnoreCase(String.valueOf(r.get("status")))&&r.get("dueDate")!=null&&LocalDate.parse(String.valueOf(r.get("dueDate"))).isBefore(LocalDate.now()))).count();
        long waived=rows.stream().filter(r->"WAIVED".equalsIgnoreCase(String.valueOf(r.get("status")))).count();
        return row("billed",billed,"paid",paid,"outstanding",outstanding,"collectionRate",billed.signum()==0?0:round(paid.doubleValue()*100/billed.doubleValue()),"overdueItems",overdue,"waivedItems",waived,"rows",rows);
    }

    @Transactional
    public Map<String,Object> createFeeDue(Map<String,Object> body) {
        String academicYear=String.valueOf(body.get("academicYear"));
        Long yearId=yearId(academicYear);
        Long studentId=Long.valueOf(String.valueOf(body.get("studentId")));
        Map<String,Object> student=jdbc.query("select id from student where id=? and academic_year_id=?",(rs,i)->row("id",rs.getLong(1)),studentId,yearId).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Student not found for "+academicYear));
        BigDecimal amount=new BigDecimal(String.valueOf(body.get("amount")));
        if(amount.signum()<=0) throw new IllegalArgumentException("Fee amount must be greater than zero");
        Date dueDate=sqlDate(body.get("dueDate"));
        if(dueDate==null) throw new IllegalArgumentException("Due date is required");
        String status=dueDate.toLocalDate().isBefore(LocalDate.now())?"OVERDUE":"DUE";
        jdbc.update("insert into student_fee_due(student_id,fee_type,term_name,amount,paid_amount,due_date,status) values(?,?,?,?,0,?,?)",studentId,optional(body,"feeType","TUITION"),optional(body,"term","General"),amount,dueDate,status);
        Long id=jdbc.queryForObject("select max(id) from student_fee_due where student_id=?",Long.class,studentId);
        return feeDue(id);
    }

    public Map<String,Object> feeDue(Long id){
        return jdbc.query("""
            select fd.id,s.id student_id,s.full_name,s.admission_no,c.class_code,c.display_name,fd.fee_type,fd.term_name,
                   fd.amount,fd.paid_amount,fd.due_date,fd.status,fd.paid_on,fd.payment_mode,fd.reference_no
            from student_fee_due fd join student s on s.id=fd.student_id join class_config c on c.id=s.class_config_id where fd.id=?
            """,(rs,i)->{BigDecimal amount=nvl(rs.getBigDecimal("amount"));BigDecimal paid=nvl(rs.getBigDecimal("paid_amount"));String status=rs.getString("status");return row("id",rs.getLong("id"),"studentId",rs.getLong("student_id"),"studentName",rs.getString("full_name"),"admissionNo",rs.getString("admission_no"),"classCode",rs.getString("class_code"),"className",rs.getString("display_name"),"feeType",rs.getString("fee_type"),"term",rs.getString("term_name"),"amount",amount,"paid",paid,"outstanding","WAIVED".equalsIgnoreCase(status)?BigDecimal.ZERO:amount.subtract(paid).max(BigDecimal.ZERO),"dueDate",date(rs.getDate("due_date")),"status",status,"paidOn",date(rs.getDate("paid_on")),"paymentMode",rs.getString("payment_mode"),"referenceNo",rs.getString("reference_no"));},id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Fee item not found: "+id));
    }

    @Transactional
    public Map<String,Object> updateFeeDue(Long id, Map<String,Object> body){
        Map<String,Object> current=feeDue(id);
        BigDecimal paid=nvl((BigDecimal)current.get("paid"));
        BigDecimal amount=body.get("amount")==null?nvl((BigDecimal)current.get("amount")):new BigDecimal(String.valueOf(body.get("amount")));
        if(amount.signum()<=0) throw new IllegalArgumentException("Fee amount must be greater than zero");
        if(amount.compareTo(paid)<0) throw new IllegalArgumentException("Fee amount cannot be less than amount already paid");
        Date dueDate=body.get("dueDate")==null?Date.valueOf(String.valueOf(current.get("dueDate"))):sqlDate(body.get("dueDate"));
        String currentStatus=String.valueOf(current.get("status"));
        String status;
        if("WAIVED".equalsIgnoreCase(currentStatus)) status="WAIVED";
        else if(paid.compareTo(amount)>=0) status="PAID";
        else if(paid.signum()>0) status="PARTIAL";
        else status=dueDate.toLocalDate().isBefore(LocalDate.now())?"OVERDUE":"DUE";
        jdbc.update("update student_fee_due set fee_type=?,term_name=?,amount=?,due_date=?,status=? where id=?",optional(body,"feeType",String.valueOf(current.get("feeType"))),optional(body,"term",String.valueOf(current.get("term"))),amount,dueDate,status,id);
        return feeDue(id);
    }

    @Transactional
    public Map<String,Object> waiveFee(Long id){
        Map<String,Object> current=feeDue(id);
        if(nvl((BigDecimal)current.get("paid")).signum()>0) throw new IllegalArgumentException("A fee with recorded payments cannot be waived. Reverse the payment first.");
        jdbc.update("update student_fee_due set status='WAIVED' where id=?",id);
        return feeDue(id);
    }

    @Transactional
    public void deleteFeeDue(Long id){
        Map<String,Object> current=feeDue(id);
        if(nvl((BigDecimal)current.get("paid")).signum()>0) throw new IllegalArgumentException("A fee with recorded payments cannot be deleted");
        jdbc.update("delete from student_fee_due where id=?",id);
    }

    @Transactional
    public void recordFeePayment(Long dueId, BigDecimal amount, String mode, String reference) {
        if(amount==null||amount.signum()<=0) throw new IllegalArgumentException("Payment amount must be greater than zero");
        Map<String,Object> current=feeDue(dueId);
        if("WAIVED".equalsIgnoreCase(String.valueOf(current.get("status")))) throw new IllegalArgumentException("Cannot record payment against a waived fee");
        BigDecimal total=nvl((BigDecimal)current.get("amount")), paid=nvl((BigDecimal)current.get("paid")), outstanding=total.subtract(paid).max(BigDecimal.ZERO);
        if(outstanding.signum()==0) throw new IllegalArgumentException("This fee is already fully paid");
        if(amount.compareTo(outstanding)>0) throw new IllegalArgumentException("Payment exceeds outstanding amount of "+outstanding);
        BigDecimal next=paid.add(amount);
        String status=next.compareTo(total)>=0?"PAID":"PARTIAL";
        jdbc.update("update student_fee_due set paid_amount=?,status=?,paid_on=current_date,payment_mode=?,reference_no=? where id=?",next,status,mode==null?"CASH":mode.trim().toUpperCase(Locale.ROOT),reference,dueId);
    }

    public List<Map<String,Object>> timetable(String academicYear,String classCode,Long teacherId) {
        Long yearId=yearId(academicYear);
        String normalizedClassCode=normalizeClassCode(classCode);
        StringBuilder sql=new StringBuilder("""
            select tt.id,c.class_code,c.display_name,tt.day_of_week,tt.period_no,tt.starts_at,tt.ends_at,tt.subject_name,
                   u.id teacher_id,u.full_name teacher,tt.room_name
            from timetable_entry tt join class_config c on c.id=tt.class_config_id left join app_user u on u.id=tt.teacher_user_id
            where c.academic_year_id=?
            """);
        List<Object> args=new ArrayList<>();args.add(yearId);
        if(normalizedClassCode!=null&&!normalizedClassCode.isBlank()){sql.append(" and c.class_code=?");args.add(normalizedClassCode);}
        if(teacherId!=null){sql.append(" and tt.teacher_user_id=?");args.add(teacherId);}
        sql.append(" order by case tt.day_of_week when 'MONDAY' then 1 when 'TUESDAY' then 2 when 'WEDNESDAY' then 3 when 'THURSDAY' then 4 when 'FRIDAY' then 5 when 'SATURDAY' then 6 else 7 end,tt.period_no,c.id");
        return jdbc.query(sql.toString(),(rs,i)->row("id",rs.getLong(1),"classCode",rs.getString(2),"className",rs.getString(3),"day",rs.getString(4),"period",rs.getInt(5),"startsAt",rs.getTime(6).toLocalTime().toString(),"endsAt",rs.getTime(7).toLocalTime().toString(),"subject",rs.getString(8),"teacherId",rs.getObject(9),"teacher",rs.getString(10),"room",rs.getString(11)),args.toArray());
    }

    @Transactional
    public void saveTimetable(String academicYear,String classCode,List<Map<String,Object>> entries) {
        Long yearId=yearId(academicYear);
        String normalizedClassCode=normalizeClassCode(classCode);
        Long classId=classId(yearId,normalizedClassCode);
        if(classId==null) throw new IllegalArgumentException("Unknown class "+classCode+" for "+academicYear);

        Set<String> days=Set.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY");
        Set<String> uniqueSlots=new HashSet<>();
        for(Map<String,Object> e: entries){
            String day=String.valueOf(e.getOrDefault("day","")).toUpperCase(Locale.ROOT);
            if(!days.contains(day)) throw new IllegalArgumentException("Invalid timetable day: "+day);
            int period=Integer.parseInt(String.valueOf(e.getOrDefault("period",0)));
            if(period<1) throw new IllegalArgumentException("Period must be 1 or greater");
            String slot=day+":"+period;
            if(!uniqueSlots.add(slot)) throw new IllegalArgumentException("Duplicate timetable slot "+slot);
            String subject=String.valueOf(e.getOrDefault("subject","")).trim();
            if(subject.isBlank()) throw new IllegalArgumentException("Subject is required for "+slot);
            java.time.LocalTime start=java.time.LocalTime.parse(String.valueOf(e.get("startsAt")));
            java.time.LocalTime end=java.time.LocalTime.parse(String.valueOf(e.get("endsAt")));
            if(!end.isAfter(start)) throw new IllegalArgumentException("End time must be after start time for "+slot);
        }

        jdbc.update("delete from timetable_entry where class_config_id=?",classId);
        for(Map<String,Object> e: entries){
            String day=String.valueOf(e.get("day")).toUpperCase(Locale.ROOT);
            int period=Integer.parseInt(String.valueOf(e.get("period")));
            java.sql.Time start=java.sql.Time.valueOf(java.time.LocalTime.parse(String.valueOf(e.get("startsAt"))));
            java.sql.Time end=java.sql.Time.valueOf(java.time.LocalTime.parse(String.valueOf(e.get("endsAt"))));
            String subject=String.valueOf(e.get("subject")).trim();
            Object teacherRaw=e.get("teacherId");
            Long teacherId=(teacherRaw==null||String.valueOf(teacherRaw).isBlank())?null:Long.valueOf(String.valueOf(teacherRaw));
            String room=String.valueOf(e.getOrDefault("room","")).trim();
            jdbc.update("insert into timetable_entry(class_config_id,day_of_week,period_no,starts_at,ends_at,subject_name,teacher_user_id,room_name) values(?,?,?,?,?,?,?,?)",
                    classId,day,period,start,end,subject,teacherId,room.isBlank()?null:room);
        }
    }

    public List<Map<String,Object>> teachers() {
        return jdbc.query("""
            select distinct u.id,u.username,u.full_name,u.email
            from app_user u join user_role r on r.user_id=u.id
            where u.enabled=true and r.role='TEACHER'
            order by u.full_name
            """,(rs,i)->row("id",rs.getLong(1),"username",rs.getString(2),"fullName",rs.getString(3),"email",rs.getString(4)));
    }

    public List<Map<String,Object>> exams(String academicYear) {
        Long yearId=yearId(academicYear);
        return jdbc.query("""
            select e.id,e.name,e.term_name,e.starts_on,e.ends_on,e.status,e.published_at,
                   (select count(*) from exam_subject es where es.exam_id=e.id) subject_count,
                   (select count(*) from student_mark sm join exam_subject es on es.id=sm.exam_subject_id where es.exam_id=e.id and sm.marks_obtained is not null) mark_count,
                   (select count(distinct es.class_config_id) from exam_subject es where es.exam_id=e.id) class_count
            from exam e where e.academic_year_id=? order by e.starts_on desc,e.id desc
            """,(rs,i)->row("id",rs.getLong(1),"name",rs.getString(2),"term",rs.getString(3),"startsOn",date(rs.getDate(4)),"endsOn",date(rs.getDate(5)),"status",rs.getString(6),"publishedAt",rs.getObject(7)==null?null:rs.getTimestamp(7).toInstant().toString(),"subjectCount",rs.getLong(8),"markCount",rs.getLong(9),"classCount",rs.getLong(10)),yearId);
    }

    @Transactional
    public Map<String,Object> createExam(Map<String,Object> body) {
        String academicYear=str(body.get("academicYear"));
        Long yearId=yearId(academicYear);
        String name=str(body.get("name"));
        if(name==null||name.isBlank()) throw new IllegalArgumentException("Exam name is required");
        Date starts=sqlDate(body.get("startsOn")), ends=sqlDate(body.get("endsOn"));
        if(starts!=null&&ends!=null&&ends.before(starts)) throw new IllegalArgumentException("Exam end date cannot be before start date");
        String status=optional(body,"status","DRAFT").toUpperCase(Locale.ROOT);
        if(!Set.of("DRAFT","SCHEDULED","ONGOING","COMPLETED","PUBLISHED").contains(status)) throw new IllegalArgumentException("Invalid exam status: "+status);
        jdbc.update("insert into exam(academic_year_id,name,term_name,starts_on,ends_on,status,published_at) values(?,?,?,?,?,?,case when ?='PUBLISHED' then current_timestamp else null end)",
                yearId,name,str(body.get("term")),starts,ends,status,status);
        Long id=jdbc.queryForObject("select max(id) from exam where academic_year_id=? and name=?",Long.class,yearId,name);
        return exams(academicYear).stream().filter(x->Objects.equals(x.get("id"),id)).findFirst().orElse(row("id",id,"name",name,"status",status));
    }

    @Transactional
    public Map<String,Object> updateExam(Long examId, Map<String,Object> body) {
        String name=str(body.get("name"));
        String term=str(body.get("term"));
        Date starts=sqlDate(body.get("startsOn")), ends=sqlDate(body.get("endsOn"));
        if(starts!=null&&ends!=null&&ends.before(starts)) throw new IllegalArgumentException("Exam end date cannot be before start date");
        String status=optional(body,"status","DRAFT").toUpperCase(Locale.ROOT);
        if(!Set.of("DRAFT","SCHEDULED","ONGOING","COMPLETED","PUBLISHED").contains(status)) throw new IllegalArgumentException("Invalid exam status: "+status);
        int n=jdbc.update("update exam set name=?,term_name=?,starts_on=?,ends_on=?,status=?,published_at=case when ?='PUBLISHED' then coalesce(published_at,current_timestamp) else published_at end where id=?",name,term,starts,ends,status,status,examId);
        if(n==0) throw new IllegalArgumentException("Unknown exam id "+examId);
        return jdbc.query("select id,name,term_name,starts_on,ends_on,status,published_at from exam where id=?",(rs,i)->row("id",rs.getLong(1),"name",rs.getString(2),"term",rs.getString(3),"startsOn",date(rs.getDate(4)),"endsOn",date(rs.getDate(5)),"status",rs.getString(6),"publishedAt",rs.getObject(7)==null?null:rs.getTimestamp(7).toInstant().toString()),examId).stream().findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> saveExamSubjects(Long examId,String academicYear,String classCode,List<Map<String,Object>> subjects) {
        Long yearId=yearId(academicYear);
        Long classId=classId(yearId,classCode);
        if(classId==null) throw new IllegalArgumentException("Class '"+classCode+"' is not configured for academic year '"+academicYear+"'");
        long examExists=count("select count(*) from exam where id=? and academic_year_id=?",examId,yearId);
        if(examExists==0) throw new IllegalArgumentException("Unknown exam id "+examId+" for "+academicYear);
        for(Map<String,Object> s:subjects){
            String subject=str(s.get("subject"));
            if(subject==null||subject.isBlank()) continue;
            BigDecimal max=new BigDecimal(String.valueOf(s.getOrDefault("maxMarks",100)));
            BigDecimal pass=new BigDecimal(String.valueOf(s.getOrDefault("passMarks",33)));
            if(max.signum()<=0) throw new IllegalArgumentException("Maximum marks must be greater than zero for "+subject);
            if(pass.signum()<0||pass.compareTo(max)>0) throw new IllegalArgumentException("Pass marks must be between 0 and maximum marks for "+subject);
            Date examDate=sqlDate(s.get("examDate"));
            Object rawId=s.get("id");
            if(rawId!=null&&!String.valueOf(rawId).isBlank()) {
                Long id=Long.valueOf(String.valueOf(rawId));
                int n=jdbc.update("update exam_subject set subject_name=?,max_marks=?,pass_marks=?,exam_date=? where id=? and exam_id=? and class_config_id=?",subject,max,pass,examDate,id,examId,classId);
                if(n==0) throw new IllegalArgumentException("Unknown exam subject id "+id);
            } else {
                List<Long> existing=jdbc.query("select id from exam_subject where exam_id=? and class_config_id=? and lower(subject_name)=lower(?)",(rs,i)->rs.getLong(1),examId,classId,subject);
                if(existing.isEmpty()) jdbc.update("insert into exam_subject(exam_id,class_config_id,subject_name,max_marks,pass_marks,exam_date) values(?,?,?,?,?,?)",examId,classId,subject,max,pass,examDate);
                else jdbc.update("update exam_subject set max_marks=?,pass_marks=?,exam_date=? where id=?",max,pass,examDate,existing.get(0));
            }
        }
        return marks(examId,academicYear,classCode,null);
    }

    @Transactional
    public void deleteExamSubject(Long subjectId) {
        long marks=count("select count(*) from student_mark where exam_subject_id=?",subjectId);
        if(marks>0) throw new IllegalArgumentException("Cannot delete a subject after marks have been entered. Clear the marks first or keep the subject in the exam.");
        jdbc.update("delete from exam_subject where id=?",subjectId);
    }

    public Map<String,Object> marks(Long examId,String academicYear,String classCode,Long guardianUserId) {
        Long yearId=yearId(academicYear); Long classId=classId(yearId,classCode);
        Map<String,Object> exam=jdbc.query(
                "select id,name,term_name,status from exam where id=?",
                (rs,i)->row("id",rs.getLong(1),"name",rs.getString(2),"term",rs.getString(3),"status",rs.getString(4)),
                examId
        ).stream().findFirst().orElse(row("id",examId,"name","Unknown exam","term",null,"status","UNKNOWN"));
        if (classId == null) {
            return row("exam",exam,"subjects",List.of(),"students",List.of());
        }
        List<Map<String,Object>> subjects=jdbc.query("select id,subject_name,max_marks,pass_marks,exam_date from exam_subject where exam_id=? and class_config_id=? order by exam_date,subject_name",(rs,i)->row("id",rs.getLong(1),"subject",rs.getString(2),"maxMarks",rs.getBigDecimal(3),"passMarks",rs.getBigDecimal(4),"examDate",date(rs.getDate(5))),examId,classId);
        String sql="select id,roll_no,full_name,admission_no from student where class_config_id=? and status='ACTIVE'"+(guardianUserId!=null?" and guardian_user_id=?":"")+" order by roll_no,full_name";
        Object[] args=guardianUserId!=null?new Object[]{classId,guardianUserId}:new Object[]{classId};
        List<Map<String,Object>> students=jdbc.query(sql,(rs,i)->row("id",rs.getLong(1),"rollNo",rs.getString(2),"fullName",rs.getString(3),"admissionNo",rs.getString(4),"marks",new ArrayList<Map<String,Object>>()),args);
        Map<Long,Map<String,Object>> byId=new LinkedHashMap<>(); students.forEach(s->byId.put((Long)s.get("id"),s));
        jdbc.query("""
            select sm.student_id,sm.exam_subject_id,es.subject_name,sm.marks_obtained,sm.grade,sm.remarks,es.max_marks,es.pass_marks
            from student_mark sm join exam_subject es on es.id=sm.exam_subject_id
            where es.exam_id=? and es.class_config_id=?
            """,rs->{
            Map<String,Object> s=byId.get(rs.getLong(1)); if(s!=null){ @SuppressWarnings("unchecked") List<Map<String,Object>> ml=(List<Map<String,Object>>)s.get("marks"); ml.add(row("examSubjectId",rs.getLong(2),"subject",rs.getString(3),"marks",rs.getBigDecimal(4),"grade",rs.getString(5),"remarks",rs.getString(6),"maxMarks",rs.getBigDecimal(7),"passMarks",rs.getBigDecimal(8))); } },examId,classId);
        return row("exam",exam,"subjects",subjects,"students",students);
    }

    @Transactional
    public void saveMarks(List<Map<String,Object>> rows, Long teacherId) {
        for(Map<String,Object> r:rows){
            Long studentId=Long.valueOf(String.valueOf(r.get("studentId"))), subjectId=Long.valueOf(String.valueOf(r.get("examSubjectId")));
            BigDecimal mark=new BigDecimal(String.valueOf(r.get("marks")));
            BigDecimal max=jdbc.query("select max_marks from exam_subject where id=?",(rs,i)->rs.getBigDecimal(1),subjectId).stream().findFirst().orElse(null);
            if(max==null) throw new IllegalArgumentException("Unknown exam subject id "+subjectId);
            if(mark.signum()<0||mark.compareTo(max)>0) throw new IllegalArgumentException("Marks must be between 0 and "+max+" for exam subject "+subjectId);
            String grade=grade(mark,max);
            int n=jdbc.update("update student_mark set marks_obtained=?,grade=?,remarks=?,entered_by_id=?,entered_at=current_timestamp where student_id=? and exam_subject_id=?",mark,grade,str(r.get("remarks")),teacherId,studentId,subjectId);
            if(n==0) jdbc.update("insert into student_mark(student_id,exam_subject_id,marks_obtained,grade,remarks,entered_by_id) values(?,?,?,?,?,?)",studentId,subjectId,mark,grade,str(r.get("remarks")),teacherId);
        }
    }

    public List<Map<String,Object>> reportCards(String academicYear,Long guardianUserId) {
        Long yearId=yearId(academicYear);
        String studentFilter=guardianUserId==null?"":" and s.guardian_user_id=?";
        String sql="""
            select s.id student_id,s.full_name,s.admission_no,s.roll_no,s.dob,s.gender,s.blood_group,s.house_name,
                   s.guardian_name,s.guardian_phone,c.class_code,c.display_name,e.id exam_id,e.name exam_name,e.term_name,
                   e.starts_on,e.ends_on,e.status exam_status,es.id exam_subject_id,es.subject_name,es.max_marks,es.pass_marks,
                   es.exam_date,sm.marks_obtained,sm.grade,sm.remarks
            from student s
            join class_config c on c.id=s.class_config_id
            join exam_subject es on es.class_config_id=c.id
            join exam e on e.id=es.exam_id and e.academic_year_id=s.academic_year_id and e.status='PUBLISHED'
            left join student_mark sm on sm.student_id=s.id and sm.exam_subject_id=es.id
            where s.academic_year_id=?"""+studentFilter+" order by e.id desc,c.id,s.full_name,es.exam_date,es.id";
        Object[] args=guardianUserId==null?new Object[]{yearId}:new Object[]{yearId,guardianUserId};
        Map<String,Map<String,Object>> cards=new LinkedHashMap<>();
        jdbc.query(sql,rs->{
            long studentId=rs.getLong("student_id"), examId=rs.getLong("exam_id");
            String key=studentId+":"+examId;
            Map<String,Object> card=cards.computeIfAbsent(key,k->{
                Map<String,Object> m=row(
                        "studentId",studentId,"studentName",safeGet(rs,"full_name"),"admissionNo",safeGet(rs,"admission_no"),
                        "rollNo",safeGet(rs,"roll_no"),"dob",date(rsDate(rs,"dob")),"gender",safeGet(rs,"gender"),
                        "bloodGroup",safeGet(rs,"blood_group"),"house",safeGet(rs,"house_name"),
                        "guardianName",safeGet(rs,"guardian_name"),"guardianPhone",safeGet(rs,"guardian_phone"),
                        "classCode",safeGet(rs,"class_code"),"className",safeGet(rs,"display_name"),"academicYear",academicYear,
                        "examId",examId,"examName",safeGet(rs,"exam_name"),"term",safeGet(rs,"term_name"),
                        "examStartsOn",date(rsDate(rs,"starts_on")),"examEndsOn",date(rsDate(rs,"ends_on")),"examStatus",safeGet(rs,"exam_status"),
                        "subjectDetails",new ArrayList<Map<String,Object>>());
                m.put("obtained",BigDecimal.ZERO); m.put("maximum",BigDecimal.ZERO); m.put("subjects",0); m.put("markedSubjects",0); m.put("failedSubjects",0);
                return m;
            });
            BigDecimal max=rs.getBigDecimal("max_marks"), pass=rs.getBigDecimal("pass_marks"), obtained=rs.getBigDecimal("marks_obtained");
            @SuppressWarnings("unchecked") List<Map<String,Object>> details=(List<Map<String,Object>>)card.get("subjectDetails");
            details.add(row("examSubjectId",rs.getLong("exam_subject_id"),"subject",safeGet(rs,"subject_name"),"examDate",date(rsDate(rs,"exam_date")),
                    "maxMarks",max,"passMarks",pass,"marks",obtained,"grade",safeGet(rs,"grade"),"remarks",safeGet(rs,"remarks"),
                    "result",obtained==null?"NOT_MARKED":(obtained.compareTo(pass)>=0?"PASS":"FAIL")));
            card.put("subjects",((Number)card.get("subjects")).intValue()+1);
            card.put("maximum",((BigDecimal)card.get("maximum")).add(max==null?BigDecimal.ZERO:max));
            if(obtained!=null){
                card.put("obtained",((BigDecimal)card.get("obtained")).add(obtained));
                card.put("markedSubjects",((Number)card.get("markedSubjects")).intValue()+1);
                if(pass!=null && obtained.compareTo(pass)<0) card.put("failedSubjects",((Number)card.get("failedSubjects")).intValue()+1);
            }
        },args);
        for(Map<String,Object> card:cards.values()){
            BigDecimal ob=(BigDecimal)card.get("obtained"), mx=(BigDecimal)card.get("maximum");
            int subjects=((Number)card.get("subjects")).intValue(), marked=((Number)card.get("markedSubjects")).intValue(), failed=((Number)card.get("failedSubjects")).intValue();
            double pct=mx.signum()==0?0:round(ob.doubleValue()*100/mx.doubleValue());
            card.put("percentage",pct);
            card.put("overallGrade",marked==0?"—":grade(ob,mx));
            card.put("result",marked<subjects?"INCOMPLETE":(failed>0?"FAIL":"PASS"));
        }
        return new ArrayList<>(cards.values());
    }

    private static String safeGet(java.sql.ResultSet rs,String column){try{return rs.getString(column);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}
    private static Date rsDate(java.sql.ResultSet rs,String column){try{return rs.getDate(column);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}

    public List<Map<String,Object>> transport(Long guardianUserId) {
        String where=guardianUserId==null?"":" where s.guardian_user_id=?"; Object[] args=guardianUserId==null?new Object[]{}:new Object[]{guardianUserId};
        return jdbc.query("""
            select r.id route_id,r.route_code,r.route_name,r.vehicle_no,r.driver_name,r.driver_phone,r.attendant_name,r.capacity,r.active route_active,
                   ts.id stop_id,ts.stop_name,ts.pickup_time,ts.drop_time,ts.seq_no,ts.monthly_fee,
                   st.id allocation_id,s.id student_id,s.full_name,c.display_name
            from transport_route r
            left join transport_stop ts on ts.route_id=r.id
            left join student_transport st on st.route_id=r.id and st.stop_id=ts.id and st.active=true
            left join student s on s.id=st.student_id
            left join class_config c on c.id=s.class_config_id
            """+where+" order by r.route_code,coalesce(ts.seq_no,999),s.full_name",(rs,i)->row(
                "routeId",rs.getLong("route_id"),"routeCode",rs.getString("route_code"),"routeName",rs.getString("route_name"),
                "vehicleNo",rs.getString("vehicle_no"),"driver",rs.getString("driver_name"),"driverPhone",rs.getString("driver_phone"),
                "attendant",rs.getString("attendant_name"),"capacity",rs.getInt("capacity"),"active",rs.getBoolean("route_active"),
                "stopId",rs.getObject("stop_id"),"stop",rs.getString("stop_name"),"pickup",rs.getTime("pickup_time")==null?null:rs.getTime("pickup_time").toLocalTime().toString(),
                "drop",rs.getTime("drop_time")==null?null:rs.getTime("drop_time").toLocalTime().toString(),"sequence",rs.getObject("seq_no"),
                "monthlyFee",rs.getBigDecimal("monthly_fee"),"allocationId",rs.getObject("allocation_id"),"studentId",rs.getObject("student_id"),
                "studentName",rs.getString("full_name"),"className",rs.getString("display_name")),args);
    }

    @Transactional public Map<String,Object> createTransportRoute(Map<String,Object> body){
        String code=str(body.get("routeCode")), name=str(body.get("routeName"));
        if(code==null||code.isBlank()||name==null||name.isBlank()) throw new IllegalArgumentException("Route code and route name are required.");
        String normalizedCode=code.trim().toUpperCase(Locale.ROOT);
        jdbc.update("insert into transport_route(route_code,route_name,vehicle_no,driver_name,driver_phone,attendant_name,capacity,active) values(?,?,?,?,?,?,?,?)",
                normalizedCode,name.trim(),str(body.get("vehicleNo")),str(body.get("driver")),str(body.get("driverPhone")),str(body.get("attendant")),
                body.get("capacity")==null?40:Integer.valueOf(String.valueOf(body.get("capacity"))), body.get("active")==null||Boolean.parseBoolean(String.valueOf(body.get("active"))));
        Long id=jdbc.queryForObject("select id from transport_route where route_code=?",Long.class,normalizedCode);
        return row("id",id,"routeCode",normalizedCode);
    }

    @Transactional public void updateTransportRoute(Long id,Map<String,Object> body){
        int n=jdbc.update("update transport_route set route_code=?,route_name=?,vehicle_no=?,driver_name=?,driver_phone=?,attendant_name=?,capacity=?,active=? where id=?",
                str(body.get("routeCode")).trim().toUpperCase(Locale.ROOT),str(body.get("routeName")).trim(),str(body.get("vehicleNo")),str(body.get("driver")),str(body.get("driverPhone")),str(body.get("attendant")),
                Integer.valueOf(String.valueOf(body.getOrDefault("capacity",40))),Boolean.parseBoolean(String.valueOf(body.getOrDefault("active",true))),id);
        if(n==0) throw new IllegalArgumentException("Transport route not found.");
    }

    @Transactional public void deleteTransportRoute(Long id){
        if(count("select count(*) from student_transport where route_id=? and active=true",id)>0) throw new IllegalArgumentException("Unassign students from this route before deleting it.");
        jdbc.update("delete from student_transport where route_id=?",id);
        jdbc.update("delete from transport_route where id=?",id);
    }

    @Transactional public Map<String,Object> saveTransportStop(Long routeId,Long stopId,Map<String,Object> body){
        String name=str(body.get("stop")); if(name==null||name.isBlank()) throw new IllegalArgumentException("Stop name is required.");
        int seq=Integer.parseInt(String.valueOf(body.getOrDefault("sequence",1)));
        java.sql.Time pickup=body.get("pickup")==null||String.valueOf(body.get("pickup")).isBlank()?null:java.sql.Time.valueOf(String.valueOf(body.get("pickup")).length()==5?String.valueOf(body.get("pickup"))+":00":String.valueOf(body.get("pickup")));
        java.sql.Time drop=body.get("drop")==null||String.valueOf(body.get("drop")).isBlank()?null:java.sql.Time.valueOf(String.valueOf(body.get("drop")).length()==5?String.valueOf(body.get("drop"))+":00":String.valueOf(body.get("drop")));
        BigDecimal fee=body.get("monthlyFee")==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(body.get("monthlyFee")));
        if(stopId==null){
            jdbc.update("insert into transport_stop(route_id,stop_name,pickup_time,drop_time,seq_no,monthly_fee) values(?,?,?,?,?,?)",routeId,name.trim(),pickup,drop,seq,fee);
            Long id=jdbc.queryForObject("select id from transport_stop where route_id=? and seq_no=?",Long.class,routeId,seq);
            return row("id",id);
        }
        int n=jdbc.update("update transport_stop set stop_name=?,pickup_time=?,drop_time=?,seq_no=?,monthly_fee=? where id=? and route_id=?",name.trim(),pickup,drop,seq,fee,stopId,routeId);
        if(n==0) throw new IllegalArgumentException("Transport stop not found."); return row("id",stopId);
    }

    @Transactional public void deleteTransportStop(Long routeId,Long stopId){
        if(count("select count(*) from student_transport where stop_id=? and active=true",stopId)>0) throw new IllegalArgumentException("Unassign students from this stop before deleting it.");
        jdbc.update("delete from student_transport where stop_id=?",stopId);
        jdbc.update("delete from transport_stop where id=? and route_id=?",stopId,routeId);
    }

    @Transactional public void assignStudentTransport(Map<String,Object> body){
        Long studentId=Long.valueOf(String.valueOf(body.get("studentId"))), routeId=Long.valueOf(String.valueOf(body.get("routeId"))), stopId=Long.valueOf(String.valueOf(body.get("stopId")));
        long valid=count("select count(*) from transport_stop where id=? and route_id=?",stopId,routeId); if(valid==0) throw new IllegalArgumentException("Selected stop does not belong to the selected route.");
        int capacity=jdbc.queryForObject("select capacity from transport_route where id=?",Integer.class,routeId);
        long assigned=count("select count(*) from student_transport where route_id=? and active=true",routeId);
        boolean already=count("select count(*) from student_transport where route_id=? and student_id=? and active=true",routeId,studentId)>0;
        if(!already && assigned>=capacity) throw new IllegalArgumentException("This route is already at capacity.");
        jdbc.update("update student_transport set active=false where student_id=? and active=true",studentId);
        Date from=sqlDate(body.get("effectiveFrom")); if(from==null) from=Date.valueOf(LocalDate.now());
        jdbc.update("insert into student_transport(student_id,route_id,stop_id,effective_from,active) values(?,?,?,?,true)",studentId,routeId,stopId,from);
    }

    @Transactional public void unassignStudentTransport(Long studentId){ jdbc.update("update student_transport set active=false where student_id=? and active=true",studentId); }

    public List<Map<String,Object>> users() {
        return jdbc.query("select id,username,full_name,email from app_user where enabled=true order by full_name",
                (rs,i)->row("id",rs.getLong(1),"username",rs.getString(2),"fullName",rs.getString(3),"email",rs.getString(4)));
    }

    public List<Map<String,Object>> notifications(Long userId, boolean allUsers) {
        if(allUsers) return jdbc.query("select n.id,n.title,n.body,n.category,n.read_flag,n.created_at,u.full_name recipient from user_notification n join app_user u on u.id=n.user_id order by n.created_at desc",(rs,i)->row("id",rs.getLong(1),"title",rs.getString(2),"body",rs.getString(3),"category",rs.getString(4),"read",rs.getBoolean(5),"createdAt",rs.getTimestamp(6).toInstant().toString(),"recipient",rs.getString(7)));
        return jdbc.query("select id,title,body,category,read_flag,created_at from user_notification where user_id=? order by created_at desc",(rs,i)->row("id",rs.getLong(1),"title",rs.getString(2),"body",rs.getString(3),"category",rs.getString(4),"read",rs.getBoolean(5),"createdAt",rs.getTimestamp(6).toInstant().toString()),userId);
    }

    @Transactional public void createNotification(Long userId,String title,String body,String category){
        if(userId==null || count("select count(*) from app_user where id=? and enabled=true",userId)==0) throw new IllegalArgumentException("Choose a valid notification recipient.");
        String cleanTitle=title==null?"":title.trim(), cleanBody=body==null?"":body.trim();
        if(cleanTitle.isBlank()) throw new IllegalArgumentException("Notification title is required.");
        if(cleanBody.isBlank()) throw new IllegalArgumentException("Notification message is required.");
        String cleanCategory=category==null||category.isBlank()?"GENERAL":category.trim().toUpperCase(Locale.ROOT);
        jdbc.update("insert into user_notification(user_id,title,body,category) values(?,?,?,?)",userId,cleanTitle,cleanBody,cleanCategory);
    }
    @Transactional public void markNotificationRead(Long id,Long userId){jdbc.update("update user_notification set read_flag=true where id=? and user_id=?",id,userId);}
    @Transactional public void markAllNotificationsRead(Long userId){jdbc.update("update user_notification set read_flag=true where user_id=? and read_flag=false",userId);}

    public List<Map<String,Object>> certificates(Long guardianUserId) {
        String where=guardianUserId==null?"":" where s.guardian_user_id=?"; Object[] args=guardianUserId==null?new Object[]{}:new Object[]{guardianUserId};
        return jdbc.query("""
            select sc.id,sc.certificate_no,sc.certificate_type,sc.title,sc.body_text,sc.issued_on,s.id student_id,s.full_name,s.admission_no,c.display_name,u.full_name issued_by
            from student_certificate sc join student s on s.id=sc.student_id join class_config c on c.id=s.class_config_id left join app_user u on u.id=sc.issued_by_id
            """+where+" order by sc.issued_on desc",(rs,i)->row("id",rs.getLong(1),"certificateNo",rs.getString(2),"type",rs.getString(3),"title",rs.getString(4),"body",rs.getString(5),"issuedOn",date(rs.getDate(6)),"studentId",rs.getLong(7),"studentName",rs.getString(8),"admissionNo",rs.getString(9),"className",rs.getString(10),"issuedBy",rs.getString(11)),args);
    }

    @Transactional public void issueCertificate(Long studentId,String type,String title,String body,Long issuerId){String no="CERT-"+System.currentTimeMillis();jdbc.update("insert into student_certificate(student_id,certificate_no,certificate_type,title,body_text,issued_by_id) values(?,?,?,?,?,?)",studentId,no,type,title,body,issuerId);}

    public String studentsCsv(String academicYear){StringBuilder b=new StringBuilder("Admission No,Roll No,Student,Class,Guardian,Phone,Status\n");for(var r:students(academicYear,null,null)) b.append(csv(r.get("admissionNo"))).append(',').append(csv(r.get("rollNo"))).append(',').append(csv(r.get("fullName"))).append(',').append(csv(r.get("className"))).append(',').append(csv(r.get("guardianName"))).append(',').append(csv(r.get("guardianPhone"))).append(',').append(csv(r.get("status"))).append('\n');return b.toString();}
    public String feesCsv(String academicYear){@SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)fees(academicYear,null,null).get("rows");StringBuilder b=new StringBuilder("Student,Admission No,Class,Fee Type,Term,Amount,Paid,Outstanding,Due Date,Status\n");for(var r:rows)b.append(csv(r.get("studentName"))).append(',').append(csv(r.get("admissionNo"))).append(',').append(csv(r.get("className"))).append(',').append(csv(r.get("feeType"))).append(',').append(csv(r.get("term"))).append(',').append(r.get("amount")).append(',').append(r.get("paid")).append(',').append(r.get("outstanding")).append(',').append(csv(r.get("dueDate"))).append(',').append(csv(r.get("status"))).append('\n');return b.toString();}

    private Long yearId(String label){return jdbc.queryForObject("select id from academic_year where label=?",Long.class,label);}
    private Long classId(Long yearId,String code){
        if (yearId == null || code == null || code.isBlank()) return null;
        String normalized=normalizeClassCode(code);
        return jdbc.query(
                "select id from class_config where academic_year_id=? and class_code=?",
                (rs,i)->rs.getLong(1),
                yearId, normalized
        ).stream().findFirst().orElse(null);
    }
    private static String normalizeClassCode(String code){
        if(code==null) return null;
        String c=code.trim().toUpperCase(Locale.ROOT);
        if(c.equals("NURSERY")) return "NUR";
        if(c.matches("CLASS_\\d+")) return "C"+c.substring("CLASS_".length());
        return c;
    }
    private long count(String sql,Object...args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;}
    private BigDecimal money(String sql,Object...args){BigDecimal n=jdbc.queryForObject(sql,BigDecimal.class,args);return n==null?BigDecimal.ZERO:n;}
    private static double round(double v){return Math.round(v*10.0)/10.0;}
    private static BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static String date(Date d){return d==null?null:d.toLocalDate().toString();}
    private static Date sqlDate(Object o){return o==null||String.valueOf(o).isBlank()?null:Date.valueOf(String.valueOf(o));}
    private static String str(Object o){return o==null?null:String.valueOf(o);}
    private static String optional(Map<String,Object>b,String k,String fallback){String v=str(b.get(k));return v==null||v.isBlank()?fallback:v;}
    private static String csv(Object v){String s=String.valueOf(v==null?"":v);return '"'+s.replace("\"","\"\"")+'"';}
    private static String grade(BigDecimal mark,BigDecimal max){double p=max.signum()==0?0:mark.doubleValue()*100/max.doubleValue();return p>=90?"A1":p>=80?"A2":p>=70?"B1":p>=60?"B2":p>=50?"C1":p>=40?"C2":p>=33?"D":"E";}
    private static Map<String,Object> row(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put(String.valueOf(kv[i]),kv[i+1]);return m;}
}
