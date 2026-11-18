package mx.cienmexicanos.question;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    private String text;
    private int points;
    private int position;

    protected Answer() {}

    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public String getText() { return text; }
    public int getPoints() { return points; }
    public int getPosition() { return position; }
}
