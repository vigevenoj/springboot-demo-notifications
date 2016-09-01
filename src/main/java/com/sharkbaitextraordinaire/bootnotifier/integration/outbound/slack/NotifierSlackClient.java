package com.sharkbaitextraordinaire.bootnotifier.integration.outbound.slack;

import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharkbaitextraordinaire.bootnotifier.config.SlackConfig;
import com.sharkbaitextraordinaire.bootnotifier.integration.HueIntegration;

import allbegray.slack.SlackClientFactory;
import allbegray.slack.rtm.Event;
import allbegray.slack.rtm.EventListener;
import allbegray.slack.rtm.SlackRealTimeMessagingClient;
import allbegray.slack.type.Channel;
import allbegray.slack.webapi.SlackWebApiClient;

@Component
public class NotifierSlackClient implements CommandLineRunner{
	
	@Autowired
	private SlackConfig slackConfig;
	private SlackRealTimeMessagingClient rtmClient;
	private final Logger logger = LoggerFactory.getLogger(NotifierSlackClient.class);
	private Channel slackChannel;
	private SlackWebApiClient slackClient;
	private SlackRtmClientRunnable slackRtmClient;
	@Autowired
	private HueIntegration hueIntegration;
	
	public NotifierSlackClient() {
	}

	@Override
	public void run(String... arg0) throws Exception {
		setUpSlack();
		slackRtmClient = new SlackRtmClientRunnable();
		slackRtmClient.start();
	}
	
	private class SlackRtmClientRunnable extends Thread {
		SlackRtmClientRunnable() {
			super("SlackRtmClientRunnable");
		}
		
		public void run() {
			logger.info("Slack rtm client integration starting up...");
			String token = slackConfig.getToken();
			
			rtmClient = SlackClientFactory.createSlackRealTimeMessagingClient(token);
			rtmClient.addListener("hello", new EventListener() {
				@Override
				public void handleMessage(JsonNode message) {
					logger.warn("Handling hello message " + message.asText());
				}
			});
			rtmClient.addListener(Event.MESSAGE, new LightingMessageEventListener(slackClient, hueIntegration.getHueSDK()));
			rtmClient.addListener(Event.MESSAGE, new ForecastMessageEventListener());
			rtmClient.connect();
		}
	}
	
	private void setUpSlack() {
		String token = slackConfig.getToken();
		String channelName = slackConfig.getChannelName();
		slackClient = SlackClientFactory.createWebApiClient(token);
		slackClient.auth();
		
		logger.debug("looking for slack channel named " + channelName);
		slackChannel = slackClient.getChannelList().stream()
				.filter(c -> c.getName().equals(channelName))
				.collect(singletonCollector());
		logger.warn("Using channel " + slackChannel.getName() + " with ID " + slackChannel.getId());
	}
	
	private static <T> Collector<T, ?, T> singletonCollector() {
	    return Collectors.collectingAndThen(
	            Collectors.toList(),
	            list -> {
	                if (list.size() != 1) {
	                    throw new IllegalStateException();
	                }
	                return list.get(0);
	            }
	    );
	}
}
