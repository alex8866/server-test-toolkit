package com.googlecode.test.toolkit.server.ssh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.googlecode.test.toolkit.server.common.exception.CommandExecuteException;
import com.googlecode.test.toolkit.server.common.exception.ServerTimeoutException;
import com.googlecode.test.toolkit.server.common.exception.UncheckedServerOperationException;
import com.googlecode.test.toolkit.server.common.user.SshUser;
import com.googlecode.test.toolkit.server.common.util.JSchUtil.JSchSessionUtil;
import com.googlecode.test.toolkit.util.CollectionUtil;
import com.googlecode.test.toolkit.util.ValidationUtil;
import com.jcraft.jsch.Session;

/**
 * @author fu.jian
 * date Jul 25, 2012
 */
public class SshServerOperations extends AbstractServerOperations {

	private static final Logger LOGGER = Logger.getLogger(SshServerOperations.class);
	
	private ExecutorService newCachedThreadPool = Executors.newCachedThreadPool();	
	private volatile Map<String, Session> ipSessionMap = new ConcurrentHashMap<String, Session>();
	private List<SshUser> allSshUsers;
	private int commandTimeOutTime = 60;


	/**
	 * after instance created, the connections will be created by default, but you should call
	 * {@link #disconnect()} to release the connections.
	 * @param atLeaseOneSshUser
	 * @param otherSshUsers
	 * @return SshServerOperations
	 * @throws UncheckedServerOperationException
	 */
	public SshServerOperations(SshUser atLeastOneSshUser, SshUser... otherSshUsers) {
  		allSshUsers = CollectionUtil.toList(atLeastOneSshUser, otherSshUsers);
 		ValidationUtil.checkNull(allSshUsers);
		connect();
	}

	@Override
	public void connect() {
		for (SshUser sshUser : allSshUsers) {
			String host = sshUser.getHost();
			if (!ipSessionMap.containsKey(host))
				synchronized (host.intern()) {
					if (!ipSessionMap.containsKey(host)) {
						Session session = JSchSessionUtil.getSession(sshUser);
						ipSessionMap.put(host, session);
					}
				}
		}
		LOGGER.info("[Server] [Complete Init IP-Session Map] " + ipSessionMap);
	}

	public int getCommandTimeOutTime() {
		return commandTimeOutTime;
	}

	public void setCommandTimeOutTime(int commandTimeOutTime) {
		this.commandTimeOutTime = commandTimeOutTime;
	}

	public List<SshUser> getAllSshUsers() {
		return allSshUsers;
	}

	@Override
	public void executeCommandHanged(String command) {
		executeCommand(command, false, true);
	}

 	@Override
	public Map<String, String> executeCommand(String command, boolean returnResult, boolean isHanged) {
		ValidationUtil.checkString(command);

		Collection<SshTask> sshTasks = formatSshTasks(command, returnResult, isHanged);
		return invokeSshTasks(sshTasks);
	}

	private Collection<SshTask> formatSshTasks(String command, boolean returnResult, boolean isHanged) {
		int initialCapacity = ipSessionMap.size();
		ValidationUtil.checkPositive(initialCapacity);

		Collection<SshTask> sshTasks = new ArrayList<SshTask>(initialCapacity);
		for (Entry<String, Session> ipSession : ipSessionMap.entrySet()) {
			Session session = ipSession.getValue();
			sshTasks.add(new SshTask(session, command, returnResult, isHanged));
		}

		return sshTasks;
	}

	private Map<String, String> invokeSshTasks(Collection<SshTask> commandTasks) {
		Map<String, String> resultMap = new HashMap<String, String>();
		List<Future<SshTaskResult<String, String>>> futures = new ArrayList<Future<SshTaskResult<String, String>>>();
		try {
			futures = newCachedThreadPool.invokeAll(commandTasks, commandTimeOutTime,
					TimeUnit.SECONDS);
			for (Future<SshTaskResult<String, String>> future : futures) {
				if (future.isCancelled()) {
					String message = String.format(" not return after: [%d][%s]",
							commandTimeOutTime, TimeUnit.SECONDS.toString());
					throw new ServerTimeoutException(message);
				}
				SshTaskResult<String, String> operationResult = future.get();
				resultMap.put(operationResult.getHost(), operationResult.getResult());
			}
		} catch (InterruptedException e) {
			LOGGER.error(e.getMessage(), e);
			throw new CommandExecuteException(e.getMessage(), e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause != null && cause instanceof UncheckedServerOperationException)
				throw (UncheckedServerOperationException) cause;
			LOGGER.error(e.getMessage(), e);
			throw new UncheckedServerOperationException(e.getMessage(), e);
		}

		return resultMap;
	}

	@Override
	public void disconnect() {
		LOGGER.info("[Server] [Release connnection] " + ipSessionMap.keySet().toString());

		disconnectSessions();
        clearConnectionMap();
	}

	private void clearConnectionMap() {
		ipSessionMap.clear();
	}

	private void disconnectSessions() {
		for (Session session : ipSessionMap.values())
			JSchSessionUtil.disconnect(session);
	}

	@Override
	public String toString() {
		return ipSessionMap.toString();
	}


}