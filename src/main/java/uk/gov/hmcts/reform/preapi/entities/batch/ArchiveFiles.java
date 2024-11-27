package uk.gov.hmcts.reform.preapi.entities.batch;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ArchiveFiles")
@XmlAccessorType(XmlAccessType.FIELD)
public class ArchiveFiles {

    @XmlElement(name = "DisplayName")
    private String displayName;
    
    @XmlElement(name = "Duration")
    private int duration;

    @XmlElement(name = "MP4FileGrp")
    private MP4FileGrp mp4FileGrp;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public MP4FileGrp getMp4FileGrp() {
        return mp4FileGrp;
    }

    public void setMp4FileGrp(MP4FileGrp mp4FileGrp) {
        this.mp4FileGrp = mp4FileGrp;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MP4FileGrp {

        @XmlElement(name = "MP4File")
        private MP4File mp4File;

        public MP4File getMp4File() {
            return mp4File;
        }

        public void setMp4File(MP4File mp4File) {
            this.mp4File = mp4File;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MP4File {

        @XmlElement(name = "CreatTime")
        private long creatTime;

        public long getCreatTime() {
            return creatTime;
        }

        public void setCreatTime(long creatTime) {
            this.creatTime = creatTime;
        }
    }
}
