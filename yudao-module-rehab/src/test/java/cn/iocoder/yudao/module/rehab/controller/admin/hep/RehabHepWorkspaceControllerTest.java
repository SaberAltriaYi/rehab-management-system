package cn.iocoder.yudao.module.rehab.controller.admin.hep;

import cn.iocoder.yudao.module.rehab.service.hep.RehabHepTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The internal gateway must never become a general-purpose HTTP proxy. */
class RehabHepWorkspaceControllerTest {
    @Test void rejectsExternalOriginsAndNonLoopbackDestinations() {
        RehabHepTokenService signer=mock(RehabHepTokenService.class);
        for(String unsafe:new String[]{
            "https://127.0.0.1:8088","http://example.com:8088",
            "http://10.0.0.3:8088","http://127.0.0.1:8088/other",
            "http://127.0.0.1:8088?redirect=https://other.example",
            "http://user@127.0.0.1:8088"}) {
            assertThrows(IllegalStateException.class,()->
                new RehabHepWorkspaceController(signer,unsafe));
        }
    }
    @Test void neverForwardsArbitraryCollectionOrIdentifiers(){
        RehabHepWorkspaceController bff=new RehabHepWorkspaceController(mock(RehabHepTokenService.class),
            "http://127.0.0.1:8088");
        assertThrows(ResponseStatusException.class,()->bff.adminList("oauth2",7));
        assertThrows(ResponseStatusException.class,()->bff.therapistList("patients/1"));
        assertThrows(ResponseStatusException.class,()->bff.therapistCompliance("../patients"));
        assertThrows(ResponseStatusException.class,()->bff.adminList("compliance",100));
    }
}
