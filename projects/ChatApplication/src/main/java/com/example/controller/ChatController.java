package com.example.controller;

import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ChatController {

	private String message;

	@PostMapping(value = "/sendMessage", produces = MediaType.TEXT_PLAIN_VALUE)
	public String sendMessage(@RequestBody String msg) {
		this.message = msg;
		System.out.println("Message has been sent: " + msg);
		return "Message sent...";
	}

	@GetMapping(value = "/receiveMessage", produces = MediaType.TEXT_PLAIN_VALUE)
	public String receiveMessage() throws Exception {
		System.out.println("Connection created...");

		while (this.message == null) {
			Thread.sleep(2000);
		}

		String response = this.message;
		this.message = null;
		System.out.println("Message is: " + response);
		return response;
	}

	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events() {
		SseEmitter emitter = new SseEmitter();
		System.out.println("Connection created...");

		new Thread(() -> {
			try {

				int count = 1;
				while (true) {
					emitter.send("Notification count: " + count + " is received...");
					System.out.println("Notificaiton count is: " + count);

					count += 1;
					Thread.sleep(2000);
				}

			} catch (Exception ex) {
				System.out.println("Closing the connection due to: " + ex.getMessage());
				emitter.complete();
			}
		}).start();

		return emitter;
	}

	@MessageMapping("/send")
	@SendTo("/topic/messages")
	public String sendMessageUsingWebSocket(String msg) {
		System.out.println("Message is: " + msg);
		return msg;
	}

}
