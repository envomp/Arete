package ee.taltech.arete_testing_service.service.git;

import ee.taltech.arete_testing_service.domain.Submission;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

//Return true = success, false = failure.

@Service
@AllArgsConstructor
public class GitPullService {

    private static final List<String> TESTABLES = List.of("ADD", "MODIFY", "RENAME", "COPY");

    private final Logger logger;
    private final TransportConfigCallback transportConfigCallback; // failed ssh will fallback onto password


    public boolean repositoryMaintenance(Submission submission) {

        String pathToStudentFolder = String.format("students/%s/%s/", submission.getUniid(), submission.getFolder());

        try {
            if (!pullOrClone(pathToStudentFolder, submission.getGitStudentRepo(), Optional.of(submission))) {
                return false;
            }
            if (submission.getSlugs() == null) {
                submission.setSlugs(getChangedFolders(pathToStudentFolder));
            }

        } catch (Exception e) {
            logger.error("Failed to read student repository.");
            return false;
        }

        return true;
    }

    public boolean pullOrClone(String pathToFolder, String pathToRepo, Optional<Submission> submission) {

        if (!SafePullAndClone(pathToFolder, pathToRepo, submission)) {
            logger.error("Defaulting to reset hard");
            if (!resetHard(pathToFolder)) {
                return false;
            }

            if (!SafePullAndClone(pathToFolder, pathToRepo, submission)) {
                logger.error("Completely failed to pull or clone.");
                return false;
            }
        }
        return true;
    }

    public HashSet<String> getChangedFolders(String pathToStudentFolder) throws IOException {
        HashSet<String> repoMainFolders = new HashSet<>();
        Repository repository = new FileRepository(pathToStudentFolder + ".git");
        RevWalk rw = new RevWalk(repository);
        ObjectId head = repository.resolve(Constants.HEAD);
        RevCommit commit = rw.parseCommit(head);
        if (commit.getParentCount() == 0) {
            // first commit, no parent. Get all slugs
            for (File file : Objects.requireNonNull(FileUtils.listFiles(
                    new File(pathToStudentFolder),
                    new RegexFileFilter("^(.*?)"),
                    DirectoryFileFilter.DIRECTORY
            ))) {
                try {
                    String potentialSlug = file.getPath().split("[/\\\\]")[3]; //students/<UNI-ID>/<Course>/<Slug or Group folder>/..
                    if (potentialSlug.matches("[a-zA-Z0-9_]*")) {
                        repoMainFolders.add(Paths.get
                                (potentialSlug,
                                        file.getAbsolutePath().split(String.format("[/\\\\]%s[/\\\\]", potentialSlug))[1])
                                .toFile().getPath()
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        } else {

            try {
                RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
                DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                df.setRepository(repository);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);
                List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                for (DiffEntry diff : diffs) {

                    try {
                        if (TESTABLES.contains(diff.getChangeType().name())) {
                            String potentialSlug = diff.getNewPath().split("[/\\\\]")[0];
                            if (potentialSlug.matches("[a-zA-Z0-9_]*")) {
                                repoMainFolders.add(diff.getNewPath());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed parsing potential slugs: {}", e.getMessage());
                    }

                }
            } catch (Exception e) {
                logger.error("Failed extracting git tree: {}", e.getMessage());
            }
        }

        return repoMainFolders;
    }

    private boolean SafePullAndClone(String pathToFolder, String pathToRepo, Optional<Submission> submission) {
        Path path = Paths.get(pathToFolder);

        if (Files.exists(path)) {
            logger.info("Checking for update for project: {}", pathToFolder);
            return SafePull(pathToFolder, submission);

        } else {

            if (!SafeClone(pathToFolder, pathToRepo, submission)) {
                return false;
            }
            logger.info("Cloned to folder: {}", pathToFolder);

            if (submission.isPresent()) { // verify and fill fields
                return SafePull(pathToFolder, submission);
            }

        }
        return true;
    }

    private boolean resetHard(String pathToFolder) {

        try {
            FileUtils.deleteDirectory(new File(pathToFolder));
            return true;
        } catch (Exception e1) {
            return false;
        }

    }

    private boolean SafePull(String pathToFolder, Optional<Submission> submission) {
        Git git = null;
        try {
            git = Git.open(new File(pathToFolder));
            if (submission.isPresent()) {
                Submission user = submission.get();

                if (submission.get().getHash() != null) {

                    try {
                        fetch(git);
                        reset(git, user);
                        RevCommit latest = getLatestCommit(git);
                        user.setCommitMessage(latest.getFullMessage());
                        logger.info("Pulled specific hash {} for user {}", user.getHash(), user.getUniid());
                    } catch (Exception e) {
                        try {
                            fixHash(git, user);
                            fetch(git);
                            reset(git, user);
                        } catch (Exception e1) {
                            logger.info("Failed to fetch and reset.");
                            git.close();
                            return false;
                        }
                    }

                } else {

                    SafePull(git);
                    RevCommit latest = getLatestCommit(git);
                    user.setHash(latest.name());
                    user.setCommitMessage(latest.getFullMessage());
                    logger.info("Pulled for user {} and set hash {}", user.getUniid(), user.getHash());
                }

            } else {

                SafePull(git);
                logger.info("Pulled to {} with hash {}", pathToFolder, getLatestCommit(git).name());
            }
            git.close();
            return true;
        } catch (Exception e) {
            if (git != null) {
                git.close();
            }
            submission.ifPresent(value -> value.setResult(e.getMessage()));
            logger.error("Pull failed with message: {}", e.getMessage());
            return false;
        }
    }

    private boolean SafeClone(String pathToFolder, String pathToRepo, Optional<Submission> submission) {
        try {
            if (System.getenv().containsKey("GIT_PASSWORD")) {
                Git git = Git.cloneRepository()
                        .setCredentialsProvider(
                                new UsernamePasswordCredentialsProvider(
                                        System.getenv().get("GIT_USERNAME"), System.getenv().get("GIT_PASSWORD")))
                        .setURI(pathToRepo)
                        .setDirectory(new File(pathToFolder))
                        .call();
                if (submission.isPresent()) {
                    RevCommit latest = getLatestCommit(git);
                    submission.get().setCommitMessage(latest.getFullMessage());
                }
                git.close();

            } else {
                Git git = Git.cloneRepository()
                        .setTransportConfigCallback(transportConfigCallback)
                        .setURI(pathToRepo)
                        .setDirectory(new File(pathToFolder))
                        .call();
                if (submission.isPresent()) {
                    RevCommit latest = getLatestCommit(git);
                    submission.get().setCommitMessage(latest.getFullMessage());
                }
                git.close();
            }
            return true;
        } catch (Exception e) {
            submission.ifPresent(value -> value.setResult(e.getMessage()));
            logger.error("Cloning failed with message: {}", e.getMessage());
            return false;
        }
    }

    private void fetch(Git git) throws GitAPIException {
        if (System.getenv().containsKey("GIT_PASSWORD")) {
            git.fetch()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                            System.getenv().get("GIT_USERNAME"), System.getenv().get("GIT_PASSWORD")))
                    .call();

        } else {
            git.fetch()
                    .setTransportConfigCallback(transportConfigCallback)
                    .call();
        }
    }

    private void reset(Git git, Submission user) throws GitAPIException {
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(user.getHash()).call();
    }

    private RevCommit getLatestCommit(Git git) throws GitAPIException, IOException {
        RevCommit youngestCommit = null;
        List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            for (Ref branch : branches) {
                RevCommit commit = walk.parseCommit(branch.getObjectId());
                if (youngestCommit == null || commit.getAuthorIdent().getWhen().compareTo(
                        youngestCommit.getAuthorIdent().getWhen()) > 0)
                    youngestCommit = commit;
            }
        }

        assert youngestCommit != null;
        return youngestCommit;
    }

    private void fixHash(Git git, Submission user) throws GitAPIException, IOException {
        RevCommit youngestCommit = null;
        List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            for (Ref branch : branches) {
                RevCommit commit = walk.parseCommit(branch.getObjectId());
                if (youngestCommit == null || commit.getAuthorIdent().getWhen().compareTo(youngestCommit.getAuthorIdent().getWhen()) > 0) {
                    if (commit.name().equals(user.getHash())) {
                        return;
                    }
                    youngestCommit = commit;
                }
            }
        }
        assert youngestCommit != null;
        logger.error("Detected faulty hash {}, replaced it with a correct one {}", user.getHash(), youngestCommit.name());
        user.setHash(youngestCommit.name());
        user.setCommitMessage(youngestCommit.getFullMessage());

    }

    private void SafePull(Git git) throws GitAPIException {
        PullResult result;
        if (System.getenv().containsKey("GIT_PASSWORD")) {
            result = git.pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                            System.getenv().get("GIT_USERNAME"), System.getenv().get("GIT_PASSWORD")))
                    .call();

        } else {
            result = git.pull()
                    .setTransportConfigCallback(transportConfigCallback)
                    .call();
        }

        assert result.isSuccessful();
    }

    public boolean resetHead(Submission submission) {

        String pathToStudentFolder = String.format("students/%s/%s/", submission.getUniid(), submission.getFolder());
        try {
            Git.open(new File(pathToStudentFolder)).reset().setMode(ResetCommand.ResetType.HARD).call();
        } catch (Exception e) {
            logger.error("Failed to reset HEAD for student. Defaulting to hard reset: {}", e.getMessage());
            if (!resetHard(pathToStudentFolder)) {
                return false;
            }
        }
        return true;
    }
}
