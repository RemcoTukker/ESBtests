/*
 * 2013
 * Copyright Remco Tukker, Almende BV 
 */
package org.jboss.soa.esb.Almende.agentService;

//name conflict, we'll have to use the full paths
//import javax.jms.Message;                 // hornetq methods needs this Message
//import org.jboss.soa.esb.message.Message; // jboss esb needs this Message 

import javax.jms.Queue;
import javax.jms.Topic;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
//import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.QueueRequestor;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MessageProducer;
import javax.naming.InitialContext;


import org.jboss.soa.esb.actions.AbstractActionLifecycle;
import org.jboss.soa.esb.helpers.ConfigTree;


import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.management.JMSManagementHelper;

public class ReceiveMessage extends AbstractActionLifecycle {
	
          int msgCount = 0;

	  protected ConfigTree	_config;
	  
	  public ReceiveMessage(ConfigTree config) { _config = config; } 
	  
	  // used to send response
	  public org.jboss.soa.esb.message.Message noOperation(org.jboss.soa.esb.message.Message message) { return message; }
	  
	  public org.jboss.soa.esb.message.Message receiveMessage(org.jboss.soa.esb.message.Message message) throws Exception{
		 
		//optimization possible:  
		//its not so nice to instantiate the whole bunch of connection, producers etc every time an agent want to register, but
   		//it will have to do for now...
		  
		    QueueConnection connection = null;
	 	    InitialContext initialContext = null;
			
	 	    try
		    {
	 	    	// ********** parse message *****************
	 	    	
	 	    	//ok, we got a message from a prospective agent. So, lets find out what queue this agent wants
			
	 	    	//System.out.println("Header: " + message.getHeader().getCall().toString());
	 	    	//System.out.println("Body: " + message.getBody().get());
			
	 	    	//TODO: do some message parsing to add more options, like removing queues or other stuff... 
	 	    	String agentName = message.getBody().get().toString(); // get the name for the queue to be created
			
			
	 	    	//  ********** create a queue *****************
			
				System.out.println("Received a registration message, trying to do management..");
         		initialContext = new InitialContext();
				QueueConnectionFactory cf = (QueueConnectionFactory)initialContext.lookup("/ConnectionFactory"); //lookup connection factory
				connection = cf.createQueueConnection("AlmendeAgentService","Nijmegen"); //create connection
				QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE); //create session
				Queue managementQueue = HornetQJMSClient.createQueue("hornetq.management"); //create special management queue
				QueueRequestor requestor = new QueueRequestor(session, managementQueue); //create queue requestor
				connection.start();
				
				javax.jms.Message m = session.createMessage(); //this is going to be the management message
				JMSManagementHelper.putOperationInvocation(m, "jms.server", "createQueue", agentName); //this is doing what I want!
				System.out.println("Sending message" + m );
				javax.jms.Message reply = requestor.request(m);	
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
         		if (!success)
         		{ 
         			System.out.println("Warning: creating queue  " + agentName + "  has failed!");
         			return message;
         		} else {
         			System.out.println("Created queue  " + agentName);
         		}
				
         		// examples of other possible management operations
				//JMSManagementHelper.putAttribute(m, "jms.queue.agentRegistrationQueue", "messageCount");
				//int messageCount = (Integer)JMSManagementHelper.getResult(reply);
        	 	//JMSManagementHelper.putOperationInvocation(m, "jms.queue.exampleQueue", "removeMessage", message.getJMSMessageID());
				         		
         		// ********** notify agents *****************
         		Topic agentNotificationTopic = (Topic) initialContext.lookup("/topic/agentNotificationTopic");
         		
         		MessageProducer producer = session.createProducer(agentNotificationTopic);
         		TextMessage message2 = session.createTextMessage("Hello, HornetQ!");
         		producer.send(message2);
         		producer.send(m);
         		
         		System.out.println("Sent notification to agent notification topic");
         		
				return message;
			}
			catch (Exception e)
			{
				System.out.println("exception generated!");
			}
			finally
			{
				if (initialContext != null) initialContext.close();
                if (connection != null) connection.close();
			}

		   return message;
	  }
	
}