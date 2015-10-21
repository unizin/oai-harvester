package org.unizin.cmp.oai.harvester.response;

import org.unizin.cmp.oai.harvester.HarvestNotification;

/**
 * Implementations monitor harvests and produce {@link OAIEventHandler}
 * instances to consume {@link javax.xml.stream.events.XMLEvent XMLEvents} from
 * OAI-PMH responses.
 * <p>
 * Instances may have internal state that is affected by calls to the various
 * methods. For example, an implementation could return a different event
 * handler for each response by creating a new instance in the
 * {@link #onResponseReceived(HarvestNotification)} method and returning that
 * instance from {@link #getEventHandler(HarvestNotification)}.
 *
 */
public interface OAIResponseHandler {
	OAIEventHandler getEventHandler(HarvestNotification notification);
	
	/**
	 * Called whenever a harvest starts.
	 * 
	 * @param notification
	 */
	void onHarvestStart(HarvestNotification notification);
	
	/**
	 * Called whenever a harvest ends.
	 * <p>
	 * Always called, whether the harvest ended normally or was terminated by
	 * an error of some kind.
	 * 
	 * @param notification
	 */
	void onHarvestEnd(HarvestNotification notification);
	
	/**
	 * Called whenever the harvester has received a response from the server,
	 * but before it has been processed.
	 * <p>
	 * Always called, regardless of the nature of the HTTP response from the
	 * server. 
	 * <p>
	 * For list requests, this will be called just before each incomplete list
	 * received from the server is processed.
	 *  
	 * @param notification
	 */
	void onResponseReceived(HarvestNotification notification);
	
	/** 
	 * Called when the harvester has finished processing a response from the 
	 * server.
	 * <p>
	 * Called whether processing succeeded or failed. Check the notification
	 * object for more information.
	 * <p>
	 * For list requests, this will be called just after each incomplete list
	 * received from the server is processed. 
	 * 
	 * @param notification 
	 */
	void onResponseProcessed(HarvestNotification notification);
}
