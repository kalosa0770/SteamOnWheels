package com.steamonwheels;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lessons")
public class Lesson {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String subject;
    public String topicEnglish;
    public String topicBemba;
    public String contentEnglish;
    public String contentBemba;

    public Lesson(String subject, String topicEnglish, String topicBemba, String contentEnglish, String contentBemba) {
        this.subject = subject;
        this.topicEnglish = topicEnglish;
        this.topicBemba = topicBemba;
        this.contentEnglish = contentEnglish;
        this.contentBemba = contentBemba;
    }
}