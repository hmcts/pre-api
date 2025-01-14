package uk.gov.hmcts.reform.preapi.entities.batch;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

@XmlRootElement(name = "ArchiveFiles")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLArchiveFileData {

    @XmlElement(name = "ArchiveID")
    private String id;
    
    @XmlElement(name = "DisplayName")
    private String displayName;
    
    @XmlElement(name = "Duration")
    private int duration;

    @XmlElement(name = "MP4FileGrp")
    private MP4FileGrp mp4FileGrp;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
        private List<MP4File> mp4Files;

        public List<MP4File> getMp4Files() {
            return mp4Files;
        }

        public void setMp4Files(List<MP4File> mp4Files) {
            this.mp4Files = mp4Files;
        }
    }


    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MP4File {

        @XmlElement(name = "CreatTime")
        private long creatTime;

        @XmlElement(name = "Name")
        private String name;

        @XmlElement(name = "Duration")
        private int duration;

        public long getCreatTime() {
            return creatTime;
        }

        public void setCreatTime(long creatTime) {
            this.creatTime = creatTime;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }
}
