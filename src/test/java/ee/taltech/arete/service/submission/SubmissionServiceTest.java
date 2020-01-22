package ee.taltech.arete.service.submission;

import ee.taltech.arete.domain.Submission;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static ee.taltech.arete.initializers.SubmissionInitializer.getGitPullEndpointSubmission;

@AutoConfigureTestDatabase
@RunWith(SpringRunner.class)
@SpringBootTest
class SubmissionServiceTest {

	private Submission submission = getGitPullEndpointSubmission();

	@Autowired
	private SubmissionService submissionService;

	@Test
	void getSubmissions() {
		submissionService.saveSubmission(submission);
		assert submissionService.getSubmissions().size() > 0;
	}

	@Test
	void getSubmissionByHash() {
		// given, when
		submissionService.saveSubmission(submission);

		// then
		assert submissionService.getSubmissionByHash(submission.getHash()).get(0).getUniid().equals(submission.getUniid());
	}

	@Test
	void fixRepo() {
		// given
		Submission submission = new Submission();
		submission.setGitStudentRepo("https://gitlab.cs.ttu.ee/envomp/iti0102-2019");

		// when
		String fixed = submissionService.fixRepository(submission.getGitStudentRepo());

		// then
		assert fixed.equals("git@gitlab.cs.ttu.ee:envomp/iti0102-2019.git") || fixed.equals("https://gitlab.cs.ttu.ee/envomp/iti0102-2019.git");

	}

}
