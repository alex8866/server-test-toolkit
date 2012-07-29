package org.test.toolkit.server.ftp.command;

import java.io.InputStream;

import org.test.toolkit.util.ValidationUtil;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class PutSftpCommand extends SftpCommandWithoutResult {

	private InputStream srcInputStream;
	private String dstFolder;
	private String dstFileName;

	public PutSftpCommand(Session session, InputStream srcInputStream, String dstFolder,
			String dstFileName) {
		super(session);
		ValidationUtil.nonNull(srcInputStream, dstFileName);

		this.srcInputStream = srcInputStream;
		this.dstFolder = dstFolder;
		this.dstFileName = dstFileName;
	}

	@Override
	protected void executeWithoutResult(ChannelSftp channelSftp) throws SftpException {
		if (dstFolder != null)
			channelSftp.cd(dstFolder);
		channelSftp.put(srcInputStream, dstFileName);
	}

}