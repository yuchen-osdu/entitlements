/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.jdbc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.jdbc.config.properties.OpenIdProviderProperties;

@ExtendWith(MockitoExtension.class)
class IDTokenValidatorFactoryTest {

    private static final String CLIENT_ID = "test-client";
    private static final Issuer ISSUER = new Issuer("https://accounts.google.com");

    @Mock
    private EntOpenIDProviderConfig entOpenIDProviderConfig;
    @Mock
    private OpenIdProviderProperties openIdConfigurationProperties;
    @Mock
    private OIDCProviderMetadata providerMetadata;

    @InjectMocks
    private IDTokenValidatorFactory sut;

    private static JWSAlgorithm algorithmOf(IDTokenValidator validator) {
        // IDTokenValidator does not expose a direct algorithm getter; peek via its JWSKeySelector.
        return ((JWSVerificationKeySelector<?>) validator.getJWSKeySelector()).getExpectedJWSAlgorithm();
    }

    @BeforeEach
    void setUp() {
        // Lenient because the unsupported-scheme test overrides providerMetadata with a different
        // mock, making the shared setUp stubs go unused for that test.
        lenient().when(entOpenIDProviderConfig.getProviderMetadata()).thenReturn(providerMetadata);
        lenient().when(providerMetadata.getIssuer()).thenReturn(ISSUER);
        lenient().when(providerMetadata.getJWKSetURI()).thenReturn(URI.create("https://accounts.google.com/jwks.json"));
    }

    @Test
    void createTokenValidator_usesFirstAlgorithmFromProviderMetadata_whenAvailable() {
        // Provider advertises RS512 first — factory must pick that over configured algorithm.
        when(providerMetadata.getIDTokenJWSAlgs())
                .thenReturn(Arrays.asList(JWSAlgorithm.RS512, JWSAlgorithm.RS256));

        IDTokenValidator validator = sut.createTokenValidator(CLIENT_ID);

        assertNotNull(validator);
        assertEquals(CLIENT_ID, validator.getClientID().getValue());
        assertEquals(ISSUER, validator.getExpectedIssuer());
        assertEquals(JWSAlgorithm.RS512, algorithmOf(validator));
    }

    @Test
    void createTokenValidator_fallsBackToConfiguredAlgorithm_whenMetadataAlgsAreNull() {
        when(providerMetadata.getIDTokenJWSAlgs()).thenReturn(null);
        when(openIdConfigurationProperties.getAlgorithm()).thenReturn("ES256");

        IDTokenValidator validator = sut.createTokenValidator(CLIENT_ID);

        assertNotNull(validator);
        assertEquals(JWSAlgorithm.ES256, algorithmOf(validator));
    }

    @Test
    void createTokenValidator_fallsBackToConfiguredAlgorithm_whenMetadataAlgsAreEmpty() {
        when(providerMetadata.getIDTokenJWSAlgs()).thenReturn(Collections.emptyList());
        when(openIdConfigurationProperties.getAlgorithm()).thenReturn("HS256");

        IDTokenValidator validator = sut.createTokenValidator(CLIENT_ID);

        assertNotNull(validator);
        assertEquals(JWSAlgorithm.HS256, algorithmOf(validator));
    }

    @Test
    void createTokenValidator_defaultsToRS256_whenNoMetadataAndNoConfiguredAlgorithm() {
        when(providerMetadata.getIDTokenJWSAlgs()).thenReturn(null);
        when(openIdConfigurationProperties.getAlgorithm()).thenReturn(null);

        IDTokenValidator validator = sut.createTokenValidator(CLIENT_ID);

        assertEquals(JWSAlgorithm.RS256, algorithmOf(validator));
    }

    @Test
    void createTokenValidator_throwsAppException_whenJwkSetUriSchemeHasNoUrlHandler() {
        // "foo-unknown-scheme://example.com/jwks.json" is a perfectly valid RFC 3986 URI and parses
        // cleanly at URI.create(...). The failure surfaces later at URI.toURL(), which throws
        // MalformedURLException because no protocol handler is registered for the
        // "foo-unknown-scheme" scheme. The factory must map that to a 500 AppException.
        OIDCProviderMetadata badMeta = mock(OIDCProviderMetadata.class);
        when(entOpenIDProviderConfig.getProviderMetadata()).thenReturn(badMeta);
        when(badMeta.getJWKSetURI()).thenReturn(URI.create("foo-unknown-scheme://example.com/jwks.json"));

        AppException ex = assertThrows(AppException.class,
                () -> sut.createTokenValidator(CLIENT_ID));
        assertEquals(500, ex.getError().getCode());
    }

    @Test
    void uriCreate_rejectsGenuinelyMalformedUri_synchronously() {
        // Contrast with the unsupported-scheme case above: a syntactically invalid URI (an illegal
        // space character in the authority) is rejected up-front by URI.create(...) with an
        // IllegalArgumentException. Such input never reaches the factory or its toURL() call, so the
        // 500-AppException mapping is not the code path exercised by malformed URIs.
        assertThrows(IllegalArgumentException.class,
                () -> URI.create("foo-unknown-scheme://exa mple.com/jwks.json"));
    }

    @Test
    void createTokenValidator_propagatesClientIdToValidator() {
        when(providerMetadata.getIDTokenJWSAlgs())
                .thenReturn(Collections.singletonList(JWSAlgorithm.RS256));

        IDTokenValidator validator = sut.createTokenValidator("another-client");
        assertEquals("another-client", validator.getClientID().getValue());
    }
}
