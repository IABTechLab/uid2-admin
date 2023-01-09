package com.uid2.admin.util;

import com.uid2.admin.model.Site;
import com.uid2.shared.Const;
import com.uid2.shared.auth.OperatorKey;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SyncedSiteDataGeneratorTest {
    
    @Test
    void testGenerateSiteData() {
        Site[] sites = {new Site(Const.Data.AdvertisingTokenSiteId, "1", true),
                new Site(3, "2", true),
                new Site(4, "3", true),
                new Site(5, "4", true)
        };
        OperatorKey[] publicOperatorKeys = {new OperatorKey("key2", "name2", "contact2", "aws-nitro", 2, false, Const.Data.AdvertisingTokenSiteId, true),
                new OperatorKey("key5", "name5", "contact5", "aws-nitro", 5, false, 5, true),
        };

        OperatorKey[] privateOperatorKeys = {new OperatorKey("key3", "name3", "contact3", "aws-nitro", 3, false, 3, false),
                new OperatorKey("key4", "name4", "contact4", "aws-nitro", 4, false, 4, false),
        };

        List<OperatorKey> allOperatorKeys = new ArrayList<OperatorKey>(Arrays.asList(publicOperatorKeys));
        allOperatorKeys.addAll(Arrays.asList(privateOperatorKeys));

        Map<Integer, Collection<Site>> result = SyncedSiteDataGenerator.generateSiteData(Arrays.asList(sites), allOperatorKeys);

        assertTrue(Arrays.stream(privateOperatorKeys).allMatch(key -> result.containsKey(key.getSiteId())));
        assertTrue(Arrays.stream(publicOperatorKeys).noneMatch(key -> result.containsKey(key.getSiteId())));

        assertFalse(result.containsKey(Const.Data.AdvertisingTokenSiteId));
        assertTrue(result.containsKey(3));
        assertTrue(result.containsKey(4));
        assertFalse(result.containsKey(5));

        //checks that Site 3 contains only SiteId=2 and its own site 3 data
        assertTrue(result.get(3).contains(sites[0]));
        assertTrue(result.get(3).contains(sites[1]));
        assertTrue(!result.get(3).contains(sites[2]));
        assertTrue(!result.get(3).contains(sites[3]));
        assertEquals(result.get(3).size(), 2);

        //checks that Site 4 contains only SiteId=2 and its own site 4 data
        assertTrue(result.get(4).contains(sites[0]));
        assertTrue(result.get(4).contains(sites[2]));
        assertTrue(!result.get(4).contains(sites[1]));
        assertTrue(!result.get(4).contains(sites[3]));
        assertEquals(result.get(4).size(), 2);
    }
}
