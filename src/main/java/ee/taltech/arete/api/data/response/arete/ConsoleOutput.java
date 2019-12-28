package ee.taltech.arete.api.data.response.arete;

import lombok.*;

import javax.persistence.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "console_output")
@Entity
public class ConsoleOutput {

	@Column(columnDefinition = "TEXT")
	String content;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

}