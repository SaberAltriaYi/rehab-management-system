package cn.iocoder.yudao.module.rehab.controller.admin.hep;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Core-side private port. HEP never reads Core tables directly. */
@RestController
@RequestMapping("/rehab/hep-bridge")
@TenantIgnore // Machine routes validate tenant/patient/episode explicitly; no logged-in Core tenant context.
@ConditionalOnProperty(name="REHAB_HEP_BRIDGE_ENABLED",havingValue="true")
public class RehabHepBridgeController {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");
    private static final Set<String> TYPES = Set.of("HEP_SESSION_COMPLETED","HEP_RISK_REPORTED","HEP_RISK_REVIEWED","HEP_FEEDBACK_REVIEWED");
    private final JdbcTemplate db;
    private final String secret;
    public RehabHepBridgeController(JdbcTemplate db, @Value("${REHAB_HEP_BRIDGE_TOKEN:}") String secret) {
        this.db=db; this.secret=secret;
    }
    public static class Binding {
        public Long tenantId, patientId, episodeId, therapistUserId;
        public Long patientAppUserId;
    }
    public static class Event {
        public String eventId, type, sessionId, payload;
        public Long tenantId, patientId, episodeId;
    }
    private void authorize(String token) {
        // Equal-length constant-time comparison to avoid leaking machine secret.
        if (secret.length()<32 || token==null ||
            !java.security.MessageDigest.isEqual(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"machine token invalid");
        }
    }
    private static void require(boolean yes) {
        if (!yes) throw new IllegalArgumentException("invalid or cross-tenant HEP bridge record");
    }
    private boolean existsPatient(long tenant,long patient) {
        return db.queryForObject("SELECT COUNT(*) FROM rehab_patient WHERE tenant_id=? AND id=? AND deleted=0",Integer.class,tenant,patient)==1;
    }
    private boolean existsEpisode(long tenant,long patient,long episode) {
        return db.queryForObject("SELECT COUNT(*) FROM rehab_episode WHERE tenant_id=? AND id=? AND patient_id=? AND deleted=0 AND status IN ('active','paused')",
            Integer.class,tenant,episode,patient)==1;
    }
    @PostMapping("/internal/verify-binding")
    @PermitAll
    public ResponseEntity<?> verify(@RequestHeader(value="X-HEP-Core-Token",required=false) String token,
                                     @RequestBody Binding request) {
        try {
            authorize(token);
            require(request!=null && request.tenantId!=null && request.tenantId>0 &&
                request.patientId!=null && request.patientId>0 && request.episodeId!=null &&
                request.episodeId>0 && request.therapistUserId!=null && request.therapistUserId>0 &&
                request.patientAppUserId!=null && request.patientAppUserId>0);
            long t=request.tenantId,p=request.patientId,e=request.episodeId,u=request.therapistUserId;
            require(existsPatient(t,p) && existsEpisode(t,p,e));
            require(db.queryForObject("SELECT COUNT(*) FROM rehab_therapist_assignment WHERE tenant_id=? AND patient_id=? AND therapist_user_id=? AND assign_status='active' AND deleted=0",
                Integer.class,t,p,u)>0);
            require(db.queryForObject("SELECT COUNT(*) FROM rehab_patient_user_binding WHERE patient_id=? AND app_user_id=? AND bind_status='active' AND deleted=0",
                Integer.class,p,request.patientAppUserId)>0);
            return ResponseEntity.ok(Map.of("verified",true,"tenantId",t,"patientId",p,"episodeId",e,"therapistUserId",u));
        } catch (org.springframework.web.server.ResponseStatusException e) {return ResponseEntity.status(401).body(Map.of("error","UNAUTHORIZED"));}
          catch (IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("error","BINDING_NOT_VERIFIED"));}
    }
    @PostMapping("/internal/events")
    @PermitAll
    public ResponseEntity<?> receive(@RequestHeader(value="X-HEP-Core-Token",required=false) String token,
                                      @RequestBody Event event) {
        try {
            authorize(token);
            require(event!=null && event.eventId!=null && EVENT_ID.matcher(event.eventId).matches() &&
                event.tenantId!=null && event.tenantId>0 && event.patientId!=null && event.patientId>0 &&
                event.episodeId!=null && event.episodeId>0 && TYPES.contains(event.type) &&
                event.sessionId!=null && EVENT_ID.matcher(event.sessionId).matches() &&
                event.payload!=null && event.payload.length()<=12000);
            require(existsPatient(event.tenantId,event.patientId) &&
                existsEpisode(event.tenantId,event.patientId,event.episodeId));
            // Enforce eventId immutability. Replays cannot rewrite clinical history.
            List<Map<String,Object>> old=db.queryForList(
                "SELECT patient_id,episode_id,event_type,session_id,payload_json FROM rehab_hep_bridge_event WHERE tenant_id=? AND event_id=?",
                event.tenantId,event.eventId);
            if (!old.isEmpty()) {
                Map<String,Object> prior=old.get(0);
                require(((Number)prior.get("patient_id")).longValue()==event.patientId &&
                    ((Number)prior.get("episode_id")).longValue()==event.episodeId &&
                    event.type.equals(prior.get("event_type")) &&
                    event.sessionId.equals(prior.get("session_id")) &&
                    event.payload.equals(prior.get("payload_json")));
                return ResponseEntity.ok(Map.of("accepted",true,"duplicate",true));
            }
            try {
                db.update("INSERT INTO rehab_hep_bridge_event(tenant_id,event_id,patient_id,episode_id,event_type,session_id,payload_json) VALUES(?,?,?,?,?,?,?)",
                    event.tenantId,event.eventId,event.patientId,event.episodeId,event.type,event.sessionId,event.payload);
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                // Concurrent retries are idempotent only for identical content.
                return receive(token,event);
            }
            return ResponseEntity.ok(Map.of("accepted",true,"duplicate",false));
        } catch (org.springframework.web.server.ResponseStatusException e) {return ResponseEntity.status(401).body(Map.of("error","UNAUTHORIZED"));}
          catch (IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("error","EVENT_NOT_VERIFIED"));}
    }
    @GetMapping("/patients/{patientId}/events")
    @PreAuthorize("@ss.hasPermission('rehab:hep:feedback:review')")
    public CommonResult<List<Map<String,Object>>> patientEvents(@PathVariable Long patientId) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        require(patientId!=null && existsPatient(tenant,patientId));
        require(db.queryForObject("SELECT COUNT(*) FROM rehab_therapist_assignment WHERE tenant_id=? AND patient_id=? AND therapist_user_id=? AND assign_status='active' AND deleted=0",
            Integer.class,tenant,patientId,getLoginUserId())>0);
        return CommonResult.success(db.queryForList(
            "SELECT id,event_id,event_type,episode_id,session_id,payload_json,review_status,created_at FROM rehab_hep_bridge_event WHERE tenant_id=? AND patient_id=? ORDER BY created_at DESC LIMIT 100",
            tenant,patientId));
    }
    @PostMapping("/patients/{patientId}/events/{eventId}/review")
    @PreAuthorize("@ss.hasPermission('rehab:hep:feedback:review')")
    public CommonResult<Boolean> review(@PathVariable Long patientId,@PathVariable String eventId) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        require(patientId!=null && existsPatient(tenant,patientId) && eventId!=null &&
            EVENT_ID.matcher(eventId).matches());
        int updated=db.update("UPDATE rehab_hep_bridge_event SET review_status='REVIEWED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP " +
            "WHERE tenant_id=? AND patient_id=? AND event_id=? AND review_status='PENDING' " +
            "AND EXISTS (SELECT 1 FROM rehab_therapist_assignment a WHERE a.tenant_id=? AND a.patient_id=? " +
            "AND a.therapist_user_id=? AND a.assign_status='active' AND a.deleted=0)",
            getLoginUserId(),tenant,patientId,eventId,tenant,patientId,getLoginUserId());
        require(updated==1);
        return CommonResult.success(true);
    }
}
