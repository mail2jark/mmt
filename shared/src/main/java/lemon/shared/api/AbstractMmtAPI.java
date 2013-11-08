package lemon.shared.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import lemon.shared.access.AccessToken;
import lemon.shared.access.AccessTokenLog;
import lemon.shared.access.ReturnCode;
import lemon.shared.access.Access;
import lemon.shared.access.persistence.AccessRepository;
import lemon.shared.config.MMTConfig;
import lemon.shared.message.log.MsgLog;
import lemon.shared.message.log.MsgLogManager;
import lemon.shared.service.ServiceType;
import lemon.shared.toolkit.http.HttpConnector;
import lemon.shared.toolkit.secure.SecureUtil;

/**
 * Abstract MMT API
 * @author lemon
 * @version 1.0
 *
 */
public abstract class AbstractMmtAPI implements MmtAPI {
	private static Log logger = LogFactory.getLog(AbstractMmtAPI.class);
	@Autowired
	private MsgLogManager msgLogManager;
	@Autowired
	private AccessRepository accessRepository;
	
	/**
	 * 获取通用接口URL
	 * @return
	 */
	public abstract String getCommonUrl();
	
	/**
	 * 获取AccessToken请求的参数
	 * @param mmt_token
	 * @return
	 */
	public abstract Map<String, Object> getAccessTokenRequestParams(MMTConfig config);
	
	/**
	 * 发送错误信息
	 * @param errorMsg
	 */
	public abstract void sendError(String errorMsg);
	
	/**
	 * 获取服务类型
	 * @return
	 */
	public abstract ServiceType getServiceType();
	
	@Override
	public final String getAcessToken(MMTConfig config) {
		//从数据库读取Access Token
		AccessToken token = accessRepository.getAccessToken(config.getCust_id(), getServiceType());
		if (token != null && token.getExpire_time() >= (System.currentTimeMillis() / 1000))
			return token.getAccess_token();
			
		//请求URL
		String url = getCommonUrl();
		// 请求参数
		Map<String, Object> params = getAccessTokenRequestParams(config);
		//获取结果
		String result = HttpConnector.get(url, params);
		//save log
		AccessTokenLog log = new AccessTokenLog();
		log.setCust_id(config.getCust_id());
		log.setAppid(params.get("appid").toString());
		log.setGrant_type(params.get("grant_type").toString());
		log.setSecret(params.get("secret").toString());
		log.setResult(result);
		log.setService_type(getServiceType());
		accessRepository.saveAccessTokenLog(log);
		//parser result
		JSONObject jsonObj = JSONObject.fromObject(result);
		if(jsonObj.get("errcode") != null){
			ReturnCode rCode = (ReturnCode) JSONObject.toBean(jsonObj, ReturnCode.class);
			sendError("Get Access Token faild: " + rCode.getErrmsg());
		}
		token = (AccessToken) JSONObject.toBean(jsonObj, AccessToken.class);
		//保存
		token.setCust_id(config.getCust_id());
		//扣除20秒，为什么？
		token.setExpire_time((int)(System.currentTimeMillis()/1000) + token.getExpires_in() - 20);
		token.setService_type(getServiceType());
		accessRepository.deleteAccessToken(config.getCust_id(), getServiceType());
		accessRepository.saveAccessToken(token);
		return token.getAccess_token();
	}
	
	@Override
	public final boolean verifySignature(Access sa) {
		if (null == sa || sa.getSignature() == null)
			return false;
		// save log
		sa.setService_type(getServiceType());
		accessRepository.saveAccessLog(sa);
		// nonce,token,timestamp dictionary sort
		List<String> list = new ArrayList<>();
		list.add(sa.getNonce());
		list.add(sa.getToken());
		list.add(sa.getTimestamp_api());
		Collections.sort(list);
		StringBuilder sb = new StringBuilder();
		for (String str : list) {
			sb.append(str);
		}
		// sha1 for signature
		String sha1str = SecureUtil.sha1(sb.toString());
		logger.debug("After SHA1:" + sha1str);
		// compare
		logger.debug("signature:" + sa.getSignature());
		return sha1str.equalsIgnoreCase(sa.getSignature());
	}
	
	/**
	 * save revive message log
	 * @param cust_id
	 * @param msg
	 */
	protected int saveRecvMsgLog(int cust_id, String msg){
		MsgLog log = obtainMsgLog(cust_id, msg);
		return msgLogManager.saveRecvLog(log);
	}
	
	/**
	 * save send message log
	 * @param cust_id
	 * @param msg
	 */
	protected int saveSendMessageLog(int cust_id, String msg){
		MsgLog log = obtainMsgLog(cust_id, msg);
		return msgLogManager.saveSendLog(log);
	}
	
	/**
	 * generate message log
	 * @param cust_id
	 * @param msg
	 * @return
	 */
	private MsgLog obtainMsgLog(int cust_id, String msg){
		MsgLog log = new MsgLog();
		log.setCust_id(cust_id);
		log.setMsg(msg);
		log.setService_type(getServiceType());
		return log;
	}

}