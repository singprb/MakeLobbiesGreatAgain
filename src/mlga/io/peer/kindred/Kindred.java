package mlga.io.peer.kindred;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mlga.io.Settings;
import mlga.io.peer.IOPeer;
import mlga.ui.LoginPanel;

/**
 * The Kindred System enables tracking and mapping of PeerID->IP through the database
 * curated by all registered users.
 * @author ShadowMoose
 */
public class Kindred {
	private final String target_url = "https://mlga.rofl.wtf/submit_report.php";
	private String token = null;
	private JsonArray queue = new JsonArray();
	private boolean saving = false;
	
	/**
	 * Initializes this Kindred System object.  <br>
	 * Will prompt the user for authentication if they haven't been previously prompted.
	 */
	public Kindred(){
		if(Settings.get("kindred_token", null) == null){
			LoginPanel lp = new LoginPanel();
			lp.prompt();
			if(lp.getCode() != null){
				Settings.set("kindred_token", lp.getCode());
			}else{
				Settings.set("kindred_token", "false");
			}
		}
		this.token = Settings.get("kindred_token", "false");
		if(this.token.equals("false")){
			this.token = null;
		}
	}
	
	/**
	 * Look up the given IP hash, and attempt to generate an IOPeer object if successful.
	 * @param ip
	 * @return
	 */
	public IOPeer lookup(int ip){
		if(this.token == null){
			return null;
		}
		//TODO: Needs to be async, and probably threaded.
		// Either thread it here with a callback IOPeer object, 
		// or thread it on the IOPeer side and simply pass data back through this call.
		return null;
	}
	
	/**
	 * Adds the given IP/ID pair to this Kindred object, and prepares to submit when called.  <br>
	 * See {@link mlga.io.peer.IOPeer#addToKindred(Kindred)} for more.
	 * @param id The UID of this Peer.
	 * @param ipHash The hasehd IP, in the format IOPeer generates.
	 */
	public void addPair(String id, int ipHash){
		if(this.token==null){
			return;
		}
		JsonObject ob = new JsonObject();
		ob.addProperty("uid", id);
		ob.addProperty("ip", ipHash);
		this.queue.add(ob);
	}
	
	/**
	 * Indicates to Kindred that the list should attempt to be submitted to the DB in the near future.  <br>
	 * Kindred will delay for up to several seconds to allow the list to batch.  <br>
	 * 
	 * Calling this while Kindred is already saving will do nothing.
	 */
	public void submit(){
		if(this.token == null || this.saving || this.queue.size()==0){
			return;
		}
		System.out.println("KINDRED: Upload queued.");
		this.saving = true;
		Thread t = new Thread("Kindred"){
			public void run(){
				try{
					Thread.sleep(3000);
					System.out.println("KINDRED: Submitting data for ~["+Kindred.this.queue.size()+"] Peers...");
					Kindred.this.post();
				}catch(Exception e){e.printStackTrace();}
				Kindred.this.saving = false;
			}
		};
		t.setDaemon(true);
		t.start();
	}
	
	/**
	 * Submit the given list of IOPeers we've identified to the Kindred database.
	 * @return
	 */
	private boolean post(){
		if(this.token == null){
			return false;
		}
		Map<String,String> params = new HashMap<>();
		params.put("token", this.token);
		params.put("manifest", new Gson().toJson(this.queue));
		this.queue = new JsonArray();
		
		try{
			StringBuilder postData = new StringBuilder();
			for (Map.Entry<String,String> param : params.entrySet()) {
				if (postData.length() != 0) postData.append('&');
				postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
				postData.append('=');
				postData.append(String.valueOf(param.getValue()));
			}
			System.out.println(postData.toString());
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");
			URL url = new URL(target_url);
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			InputStream is = conn.getInputStream();
			JsonElement ele = new JsonParser().parse(new InputStreamReader(is) );
			is.close();
			JsonObject resp = ele.getAsJsonObject();
			if(resp.get("error")!=null){
				System.err.println("KINDRED: "+resp.get("error").getAsString());
				return false;
			}
			System.out.println("KINDRED: "+resp.get("success").getAsString());
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
		return true;
	}
}
