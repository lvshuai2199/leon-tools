package springboot.DTO;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeVOJsonTest {

    @Test
    void regCodeBlockUsesLockedFieldNames() throws Exception {
        MeVO vo = new MeVO();
        vo.getRegCode().setSubUser(true);
        vo.getRegCode().setMaxSubUsers(3);
        vo.getRegCode().setCreatedCount(1);
        String json = JsonMapper.builder().build().writeValueAsString(vo);
        assertTrue(json.contains("\"regCode\":{"), json);
        assertTrue(json.contains("\"isSubUser\":true"), json);
        assertFalse(json.contains("\"subUser\""), json);
        assertTrue(json.contains("\"canCreateSubUsers\":false"), json);
        assertTrue(json.contains("\"maxSubUsers\":3"), json);
        assertTrue(json.contains("\"createdCount\":1"), json);
    }
}
