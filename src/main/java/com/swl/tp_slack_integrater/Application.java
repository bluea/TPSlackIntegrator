package com.swl.tp_slack_integrater;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import lombok.extern.java.Log;

import org.apache.commons.codec.Charsets;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Log
@Controller
@EnableAutoConfiguration
public class Application {
	Cache<String, Template> templates = 
			CacheBuilder.newBuilder()
		    .concurrencyLevel(2)
		    .weakKeys()
		    .maximumSize(10000)
		    .expireAfterWrite(30, TimeUnit.MINUTES)
		    .build();

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public void recieveTPWebhook(Template template) throws IOException, ExecutionException{
        System.out.println(template.toString());
        
        Template previousTemplate = templates.getIfPresent(template.webhook);
        
        if (previousTemplate != null) {
        	Optional<String> previousState = getEntityState(previousTemplate);
        	Optional<String> currentState = getEntityState(template);
        	
        	if (!previousState.isPresent() || !currentState.isPresent() || previousState.equals(currentState)) {
        		log.info(
        				String.format(
        						"State not found or same as previous state in last 30 mins: Previous %s, Current %s",
        						previousState.toString(),
        						currentState.toString()));
        		return;
        	}
        }
        	
        templates.put(template.webhook, template);
        
        HttpPost post = new HttpPost(template.webhook);
        post.addHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        post.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(template), Charsets.UTF_8));
        
        log.info("Pushing notification to the Slack channel.");
        
        HttpClientBuilder.create().build().execute(post);
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
    
    private Optional<String> getEntityState(Template template) {
    	return Arrays
    			.stream(EntityState.values())
    			.map(e -> e.name().replace('_', ' '))
    			.filter(e -> template.text.contains(e))
    			.findFirst();
    }
}