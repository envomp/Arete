package ee.taltech.arete.service.git;

import ee.taltech.arete.domain.Submission;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;

@Service
public class GitPullServiceImpl implements GitPullService {

	private static Logger LOGGER = LoggerFactory.getLogger(GitPullService.class);
	private static final List<String> TESTABLES = List.of("ADD", "MODIFY");

	@Override
	public void repositoryMaintenance(Submission submission) {

		String pathToStudentFolder = String.format("students/%s/%s/", submission.getUniid(), submission.getProject());
		String pathToStudentRepo = String.format("https://gitlab.cs.ttu.ee/%s/%s.git", submission.getUniid(), submission.getProject());
		pullOrCloneStudentCode(submission, pathToStudentFolder, pathToStudentRepo);

		String pathToTesterFolder = String.format("tests/%s/", submission.getProject());
		String pathToTesterRepo = String.format("https://gitlab.cs.ttu.ee/%s/%s.git", submission.getProject(), submission.getProjectBase());
		pullOrCloneTesterCode(submission, pathToTesterFolder, pathToTesterRepo);
	}

	@Override
	public void pullOrCloneStudentCode(Submission submission, String pathToStudentFolder, String pathToStudentRepo) {
		Path path = Paths.get(pathToStudentFolder);

		if (Files.exists(path)) {
			LOGGER.info("Pulling a repository for student with uniid: {}", submission.getUniid());

			try {

				PullResult result = Git.open(new File(pathToStudentFolder)).pull()
						.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
								"envomp", System.getenv().get("AUTOMATED_TESTING_SERVER_SECRET_PASSWORD")))
						.call();

				assert result.isSuccessful();

				String[] slugs = getChangedFolders(pathToStudentFolder);
				submission.setSlugs(slugs);

				LOGGER.info("Pulled a repository for student with uniid: {} and added slugs: {}", submission.getUniid(), submission.getSlugs());

			} catch (Exception e) {
				e.printStackTrace();
				LOGGER.error("Pull failed for user: {} with message: {}", submission.getUniid(), e.getMessage());
			}

		} else {
			LOGGER.info("Cloning a repository for student with uniid: {}", submission.getUniid());

			try {
				Git git = Git.cloneRepository()
						.setCredentialsProvider(new UsernamePasswordCredentialsProvider("envomp", "P4x2t4a5a1"))
						.setURI(pathToStudentRepo)
						.setDirectory(new File(pathToStudentFolder))
						.call();

				String[] slugs = getChangedFolders(pathToStudentFolder);
				submission.setSlugs(slugs);
				LOGGER.info("Cloned a repository for student with uniid: {} and added slugs: {}", submission.getUniid(), submission.getSlugs());

			} catch (Exception e) {
				LOGGER.error("Clone failed for user: {} with message: {}", submission.getUniid(), e.getMessage());
			}
		}
	}

	@Override
	public void pullOrCloneTesterCode(Submission submission, String pathToStudentFolder, String pathToStudentRepo) {
		Path path = Paths.get(pathToStudentFolder);

		if (Files.exists(path)) {
			LOGGER.info("Checking for tester update for project: {}", submission.getProject());

			try {
				PullResult result = Git.open(new File(pathToStudentFolder)).pull()
						.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
								"envomp", System.getenv().get("AUTOMATED_TESTING_SERVER_SECRET_PASSWORD")))
						.call();

				assert result.isSuccessful();

			} catch (Exception e) {
				LOGGER.error("Checking failed for tester update: {}", e.getMessage());
			}

		} else {
			LOGGER.info("Cloning tester with name: {}", submission.getProject());

			try {
				Git git = Git.cloneRepository()
						.setCredentialsProvider(new UsernamePasswordCredentialsProvider("envomp", "P4x2t4a5a1"))
						.setURI(pathToStudentRepo)
						.setDirectory(new File(pathToStudentFolder))
						.call();

			} catch (Exception e) {
				LOGGER.error("Cloning tester failed with message: {}", e.getMessage());
			}
		}
	}

	private String[] getChangedFolders(String pathToStudentFolder) throws IOException {
		HashSet<String> repoMainFolders = new HashSet<>();
		Repository repository = new FileRepository(pathToStudentFolder + ".git");
		RevWalk rw = new RevWalk(repository);
		ObjectId head = repository.resolve(Constants.HEAD);
		RevCommit commit = rw.parseCommit(head);
		RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
		DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
		df.setRepository(repository);
		df.setDiffComparator(RawTextComparator.DEFAULT);
		df.setDetectRenames(true);
		List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
		for (DiffEntry diff : diffs) {
			if (TESTABLES.contains(diff.getChangeType().name())) {
				String potentialSlug = diff.getNewPath().split("/")[0];
				if (potentialSlug.matches("[a-zA-Z0-9]*")) {
					repoMainFolders.add(potentialSlug);
				}
			}
		}
		return repoMainFolders.toArray(new String[0]);
	}
}