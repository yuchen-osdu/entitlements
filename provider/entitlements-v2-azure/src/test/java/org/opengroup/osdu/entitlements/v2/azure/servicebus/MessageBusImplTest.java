package org.opengroup.osdu.entitlements.v2.azure.servicebus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.azure.publisherFacade.MessagePublisher;
import org.opengroup.osdu.azure.publisherFacade.PublisherInfo;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.entitlements.v2.azure.config.PublisherConfig;
import org.opengroup.osdu.entitlements.v2.azure.config.ServiceBusConfig;
import org.opengroup.osdu.entitlements.v2.model.events.EntitlementsChangeAction;
import org.opengroup.osdu.entitlements.v2.model.events.EntitlementsChangeEvent;
import org.opengroup.osdu.entitlements.v2.model.events.EntitlementsChangeType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class MessageBusImplTest {

    @InjectMocks
    private MessageBusImpl messageBusImpl;

    @Mock
    private ServiceBusConfig serviceBusConfig;

    @Mock
    private MessagePublisher messagePublisher;

    @Mock
    private PublisherConfig publisherConfig;

    @Test
    public void shouldPublishMessage() {
        when(publisherConfig.getPubSubBatchSize()).thenReturn("1");
        when(serviceBusConfig.getEntitlementsChangeServiceBusTopic()).thenReturn("entitlements-changed");
        EntitlementsChangeEvent[] messages = {
                EntitlementsChangeEvent.builder()
                        .kind(EntitlementsChangeType.groupChanged)
                        .group("data.x@common.contoso.com")
                        .user("member@contoso.com")
                        .action(EntitlementsChangeAction.add)
                        .modifiedBy("requesterid")
                        .modifiedOn(1291371330000L).build()
        };
        Map<String, String> headersMap = Collections.singletonMap("testKey", "testValue");
        DpsHeaders headers = DpsHeaders.createFromMap(headersMap);
        PublisherInfo publisherInfo = PublisherInfo.builder()
                .batch(messages)
                .serviceBusTopicName("entitlements-changed")
                .build();

        messageBusImpl.publish(messages, headersMap);

        verify(messagePublisher, times(1)).publishMessage(any(DpsHeaders.class), any(PublisherInfo.class), any(Optional.class));

    }

    @Test
    public void shouldPublishMultipleBatches_whenMessagesExceedBatchSize() {
        when(publisherConfig.getPubSubBatchSize()).thenReturn("10");
        when(serviceBusConfig.getEntitlementsChangeServiceBusTopic()).thenReturn("entitlements-changed");
        
        EntitlementsChangeEvent[] messages = createTestMessages(25);
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");

        messageBusImpl.publish(messages, headersMap);

        verify(messagePublisher, times(3)).publishMessage(
                any(DpsHeaders.class),
                any(PublisherInfo.class),
                eq(Optional.empty())
        );
    }

    @Test
    public void shouldNotPublish_whenNoMessages() {
        when(publisherConfig.getPubSubBatchSize()).thenReturn("10");
        EntitlementsChangeEvent[] messages = new EntitlementsChangeEvent[0];
        Map<String, String> headersMap = new HashMap<>();

        messageBusImpl.publish(messages, headersMap);

        verify(messagePublisher, never()).publishMessage(any(), any(), any());
    }

    @Test
    public void shouldUseCorrectTopicName() {
        String customTopic = "custom-entitlements-topic";
        when(publisherConfig.getPubSubBatchSize()).thenReturn("10");
        when(serviceBusConfig.getEntitlementsChangeServiceBusTopic()).thenReturn(customTopic);
        
        EntitlementsChangeEvent[] messages = createTestMessages(1);
        Map<String, String> headersMap = new HashMap<>();

        messageBusImpl.publish(messages, headersMap);

        ArgumentCaptor<PublisherInfo> publisherInfoCaptor = ArgumentCaptor.forClass(PublisherInfo.class);
        verify(messagePublisher).publishMessage(any(), publisherInfoCaptor.capture(), any());

        assertEquals(customTopic, publisherInfoCaptor.getValue().getServiceBusTopicName());
    }

    @Test
    public void shouldCreateDpsHeadersFromMap() {
        when(publisherConfig.getPubSubBatchSize()).thenReturn("10");
        when(serviceBusConfig.getEntitlementsChangeServiceBusTopic()).thenReturn("test-topic");
        
        EntitlementsChangeEvent[] messages = createTestMessages(1);
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");

        messageBusImpl.publish(messages, headersMap);

        ArgumentCaptor<DpsHeaders> headersCaptor = ArgumentCaptor.forClass(DpsHeaders.class);
        verify(messagePublisher).publishMessage(headersCaptor.capture(), any(), any());

        DpsHeaders capturedHeaders = headersCaptor.getValue();
        assertNotNull(capturedHeaders);
        assertEquals("test-partition", capturedHeaders.getPartitionId());
    }

    private EntitlementsChangeEvent[] createTestMessages(int count) {
        EntitlementsChangeEvent[] messages = new EntitlementsChangeEvent[count];
        for (int i = 0; i < count; i++) {
            messages[i] = EntitlementsChangeEvent.builder()
                    .kind(EntitlementsChangeType.groupChanged)
                    .group("group" + i + "@example.com")
                    .user("user" + i + "@example.com")
                    .action(EntitlementsChangeAction.add)
                    .modifiedBy("requester")
                    .modifiedOn(1291371330000L)
                    .build();
        }
        return messages;
    }
}