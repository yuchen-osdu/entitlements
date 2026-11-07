package org.opengroup.osdu.entitlements.v2.di;

import org.opengroup.osdu.entitlements.v2.util.FileReaderService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WhitelistSvcAccBeanConfigurationTests {

    @Mock
    private FileReaderService fileReaderService;

    @InjectMocks
    private WhitelistSvcAccBeanConfiguration sut;

    private final String WHITELISTSERVICEACCOUNT = "{\n" +
            "  \"partitionAssociationQuota\": [\n" +
            "    {\n" +
            "      \"name\": \"gateway-devportal.com@contoso.com\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"sli-core-tester@contoso.com\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    @Before
    public void setup() throws Exception {
        prepareFileReaderForUsersTesting();
        ReflectionTestUtils.invokeMethod(sut, "init");
    }

    @Test
    public void shouldReturnTrueIfWhitelistedAccount() throws Exception {
        boolean res = sut.isWhitelistedServiceAccount("gateway-devportal.com@contoso.com");
        assertTrue(res);
    }

    @Test
    public void shouldReturnFalseIfNotWhitelistedAccount() throws Exception {
        boolean res = sut.isWhitelistedServiceAccount("service-account.com@contoso.com");
        assertFalse(res);
    }

    private void prepareFileReaderForUsersTesting() {
        when(fileReaderService.readFile("/provisioning/whitelistServiceAccount/quota_white_list.json")).thenReturn(WHITELISTSERVICEACCOUNT);
    }
}
