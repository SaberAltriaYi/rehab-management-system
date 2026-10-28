package cn.iocoder.yudao.module.rehab.controller.admin.hep;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.service.hep.RehabHepTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * Same-origin BFF for the native Rehab Core HEP workspace.
 * Only explicit server-owned routes can reach the loopback HEP service.
 * Never forward browser-provided bearer tokens, destinations or tenant IDs.
 */
@RestController
@RequestMapping("/rehab/hep-workspace")
@ConditionalOnProperty(name="REHAB_HEP_BRIDGE_ENABLED",havingValue="true")
public class RehabHepWorkspaceController {
    private static final Set<String> THERAPIST_COLLECTIONS=Set.of("patients","exercises","risks","feedback","templates");
    private static final Set<String> ADMIN_COLLECTIONS=Set.of("patients","exercises","risks","feedback","plans","sessions","templates");
    private final RehabHepTokenService signing;
    private final URI base;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public RehabHepWorkspaceController(RehabHepTokenService signing,
            @Value("${REHAB_HEP_INTERNAL_BASE_URL:http://127.0.0.1:8088}") String server) {
        this.signing=signing;
        this.base=URI.create(server);
        if(!"http".equals(base.getScheme()) ||
            !Set.of("127.0.0.1","localhost").contains(base.getHost()) ||
            base.getPort()<=0 || !"/".equals(base.getPath().isEmpty()?"/":base.getPath()) ||
            base.getQuery()!=null || base.getUserInfo()!=null || base.getFragment()!=null)
            throw new IllegalStateException("HEP BFF must target a loopback HTTP backend only");
    }

    private void checkId(String id) {
        if(id==null || !id.matches("[0-9a-fA-F-]{36}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid HEP identifier");
    }
    private Object relay(String role,String method,String path,Object data) {
        Long user=getLoginUserId();
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please log in");
        try{
            String jwt=(String)signing.token(user,role).get("accessToken");
            HttpRequest.Builder req=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(9))
                .header("Authorization","Bearer "+jwt).header("Accept","application/json");
            if("GET".equals(method))req.GET();
            else req.header("Content-Type","application/json").method(method,
                HttpRequest.BodyPublishers.ofString(toJsonString(data==null?Map.of():data)));
            HttpResponse<String> response=http.send(req.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status=response.statusCode();
            if(status<200 || status>=300)
                throw new ResponseStatusException(status==403?HttpStatus.FORBIDDEN:
                    status==400||status==409?HttpStatus.CONFLICT:
                    status==404?HttpStatus.NOT_FOUND:HttpStatus.BAD_GATEWAY,
                    "HEP rejected the operation ("+status+")");
            String content=response.body();
            if(content.length()>1024*1024)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"HEP response too large");
            if(content.isEmpty())return Map.of("saved",true);
            return cn.hutool.json.JSONUtil.parse(content);
        }catch(ResponseStatusException e){throw e;}
         catch(InterruptedException e){Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"HEP request interrupted");}
         catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"HEP service unavailable");}
    }
    private CommonResult<Object> therapist(String method,String path,Object data) {
        return CommonResult.success(relay("THERAPIST",method,path,data));
    }
    private CommonResult<Object> admin(String method,String path,Object data) {
        return CommonResult.success(relay("ADMIN",method,path,data));
    }

    @GetMapping("/therapist/{collection}")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Object> therapistList(@PathVariable String collection){
        if(!THERAPIST_COLLECTIONS.contains(collection))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return therapist("GET","/api/therapist/"+collection,null);
    }
    @GetMapping("/therapist/compliance/{patientId}")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Object> therapistCompliance(@PathVariable String patientId){
        checkId(patientId);
        return therapist("GET","/api/therapist/compliance/"+patientId,null);
    }
    @GetMapping("/therapist/drafts")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:create')")
    public CommonResult<Object> drafts(){
        return therapist("GET","/api/therapist/exercises/drafts",null);
    }
    @PostMapping(value="/therapist/exercises/{id}/media",consumes="multipart/form-data")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:create')")
    public CommonResult<Object> uploadActionMedia(@PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        checkId(id);
        if(file==null || file.isEmpty() || file.getSize()>16L*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File must be 1–16 MB");
        String mime=file.getContentType();
        if(mime==null || !Set.of("image/png","image/jpeg","video/mp4","video/webm").contains(mime))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported action media");
        try {
            String jwt=(String)signing.token(getLoginUserId(),"THERAPIST").get("accessToken");
            String boundary="hep"+UUID.randomUUID().toString().replace("-","");
            String prefix="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"exercise\"\r\n"+
                "Content-Type: "+mime+"\r\n\r\n";
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            bytes.write(prefix.getBytes(StandardCharsets.UTF_8));
            bytes.write(file.getBytes());
            bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest req=HttpRequest.newBuilder(base.resolve("/api/therapist/exercises/"+id+"/media"))
                .timeout(Duration.ofSeconds(35)).header("Authorization","Bearer "+jwt)
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
            HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()!=201)
                throw new ResponseStatusException(response.statusCode()==403?HttpStatus.FORBIDDEN:HttpStatus.BAD_GATEWAY,
                    "HEP media upload rejected ("+response.statusCode()+")");
            return CommonResult.success(cn.hutool.json.JSONUtil.parse(response.body()));
        }catch(ResponseStatusException e){throw e;}
         catch(InterruptedException e){Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Media upload interrupted");}
         catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Media upload unavailable");}
    }
    @PostMapping("/therapist/exercises")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:create')")
    public CommonResult<Object> exercise(@RequestBody Map<String,Object> input) {
        return therapist("POST","/api/therapist/exercises",input);
    }
    @PostMapping("/therapist/plans")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Object> plan(@RequestBody Map<String,Object> input){
        return therapist("POST","/api/therapist/plans",input);
    }
    @PostMapping("/therapist/templates")
    @PreAuthorize("@ss.hasPermission('rehab:hep:template:create')")
    public CommonResult<Object> createTemplate(@RequestBody Map<String,Object> input){
        return therapist("POST","/api/therapist/templates",input);
    }
    @PostMapping("/therapist/templates/{id}/issue")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Object> issueTemplate(@PathVariable String id,@RequestBody Map<String,Object> input){
        checkId(id);return therapist("POST","/api/therapist/templates/"+id+"/issue",input);
    }
    @PostMapping("/therapist/import-core")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Object> importCore(@RequestBody Map<String,Object> input){
        return therapist("POST","/api/therapist/import-core-prescription",input);
    }
    @PostMapping("/therapist/risks/{id}/review")
    @PreAuthorize("@ss.hasPermission('rehab:hep:feedback:review')")
    public CommonResult<Object> reviewRisk(@PathVariable String id,@RequestBody Map<String,Object> data){
        checkId(id);return therapist("POST","/api/therapist/risks/"+id+"/review",data);
    }
    @PostMapping("/therapist/feedback/{id}/review")
    @PreAuthorize("@ss.hasPermission('rehab:hep:feedback:review')")
    public CommonResult<Object> reviewFeedback(@PathVariable String id,@RequestBody Map<String,Object> data){
        checkId(id);return therapist("POST","/api/therapist/feedback/"+id+"/review",data);
    }
    @GetMapping("/admin/overview")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> overview(){return admin("GET","/api/admin/overview",null);}
    @GetMapping("/admin/sync")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> sync(){return admin("GET","/api/admin/core-sync/status",null);}
    @GetMapping("/admin/mappings")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> mappings(){return admin("GET","/api/admin/core-exercise-mappings",null);}
    @GetMapping("/admin/{collection}")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> adminList(@PathVariable String collection,
        @RequestParam(required=false,defaultValue="7") int days){
        if("compliance".equals(collection)){
            if(days!=7 && days!=30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            return admin("GET","/api/admin/compliance?days="+days,null);
        }
        if(!ADMIN_COLLECTIONS.contains(collection))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return admin("GET","/api/admin/list/"+collection,null);
    }
    @PostMapping("/admin/bind")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> bind(@RequestBody Map<String,Object> data){
        return admin("POST","/api/admin/patient-bindings/core",data);
    }
    @PostMapping("/admin/mappings")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> map(@RequestBody Map<String,Object> data){
        return admin("POST","/api/admin/core-exercise-mappings",data);
    }
    @PostMapping("/admin/exercises/{id}/approve")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:review')")
    public CommonResult<Object> approveExercise(@PathVariable String id){
        checkId(id);return admin("POST","/api/admin/exercises/"+id+"/approve",null);
    }
    @PostMapping("/admin/templates/{id}/approve")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> approveTemplate(@PathVariable String id){
        checkId(id);return admin("POST","/api/admin/templates/"+id+"/approve",null);
    }
    @PutMapping("/admin/membership/{id}")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Object> membership(@PathVariable String id,@RequestBody Map<String,Object> data){
        checkId(id);return admin("PUT","/api/admin/patients/"+id+"/membership",data);
    }
}
