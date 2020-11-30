package ee.taltech.arete_testing_service.service;

import com.sun.management.OperatingSystemMXBean;
import ee.taltech.arete_testing_service.configuration.DevProperties;
import ee.taltech.arete_testing_service.domain.Submission;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Service
@EnableAsync
public class PriorityQueueService {

	private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

	private final Logger LOGGER = LoggerFactory.getLogger(PriorityQueueService.class);

	private final DevProperties devProperties;

	private final JobRunnerService jobRunnerService;

	private final CopyOnWriteArrayList<Submission> activeSubmissions = new CopyOnWriteArrayList<>();

	private final PriorityQueue<Submission> submissionPriorityQueue = new PriorityQueue<>(Comparator
			.comparingInt(Submission::getPriority)
			.reversed()
			.thenComparing(Submission::getReceivedTimestamp));

	private Boolean halted = true;

	private Integer jobsRan = 0;

	private Integer stuckQueue = 3000; // just some protection against stuck queue

	@Lazy
	public PriorityQueueService(DevProperties devProperties, JobRunnerService jobRunnerService) {
		this.devProperties = devProperties;
		this.jobRunnerService = jobRunnerService;
	}

	public Integer getJobsRan() {
		return jobsRan;
	}

	public void halt() throws InterruptedException {
		halted = true;
		int antiStuck = 30;
		while (activeSubmissions.size() != 0 && antiStuck != 0) {
			TimeUnit.SECONDS.sleep(1);
			antiStuck--;
		}
	}

	public void halt(int maxAllowedJobs) throws InterruptedException {
		halted = true;
		int antiStuck = 30;
		while (activeSubmissions.size() > maxAllowedJobs && antiStuck != 0) {
			TimeUnit.SECONDS.sleep(1);
			antiStuck--;
		}
	}

	public void go() {
		halted = false;
	}

	@Async
	@Scheduled(fixedRate = 10000)
	public void clearCache() {

		try {
			for (Submission submission : getActiveSubmissions()) {
				if (submission.getReceivedTimestamp() + Math.min(submission.getDockerTimeout() + 10, stuckQueue) * 1000 < System.currentTimeMillis()) {
					killThread(submission);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<Submission> getActiveSubmissions() {
		return activeSubmissions;
	}

	public void killThread(Submission submission) {
		jobsRan++;
		activeSubmissions.remove(submission);

		try {
			TimeUnit.SECONDS.sleep(10); // keep files for a little bit so mail can send them and prevent spam pushing
			FileUtils.deleteDirectory(new File(String.format("input_and_output/%s", submission.getHash())));
		} catch (Exception e) {
			LOGGER.error("Failed deleting directory after killing thread: {}", e.getMessage());
		}

		LOGGER.info("All done for submission on thread: {}", submission.getHash());
	}

	@Async
	@Scheduled(fixedRate = 100)
	public void runJob() {

		if (submissionPriorityQueue.size() == 0) {
			return;
		}

		if (halted) {
			stuckQueue--;
		} else {
			stuckQueue = 300;
		}

		if (stuckQueue <= 0) {
			halted = false;
		}

		if (!halted && getQueueSize() != 0 && isCPUAvaiable()) {

			Submission job = submissionPriorityQueue.poll();

			if (job == null) {
				return;
			}

			if (job.getPriority() < 8 && job.getUniid() != null && activeSubmissions.stream().anyMatch(o -> o.getUniid().equals(job.getUniid()))) {
				job.setPriority(4); // Mild punish for spam pushers.

				enqueue(job);
				return;
			}

			if (job.getTimestamp() == null) {
				job.setTimestamp(System.currentTimeMillis());
			}

			activeSubmissions.add(job);

			LOGGER.info("active: {}, queue: {}, ran: {}", activeSubmissions.size(), getQueueSize(), jobsRan);

			LOGGER.info("Running job for {} with hash {}", job.getUniid(), job.getHash());

			try {
				jobRunnerService.runJob(job);
			} catch (Exception e) {
				LOGGER.error("Job failed with message: {}", e.getMessage());
			} finally {
				killThread(job);
			}

		}
	}

	public Integer getQueueSize() {
		return submissionPriorityQueue.size();
	}

	private boolean isCPUAvaiable() {
		return osBean.getSystemCpuLoad() < devProperties.getMaxCpuUsage() && devProperties.getParallelJobs() > activeSubmissions.size();
	}

	public void enqueue(Submission submission) {
		submissionPriorityQueue.add(submission);
	}
}
