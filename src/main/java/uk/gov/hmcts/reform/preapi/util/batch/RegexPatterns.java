package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.regex.Pattern;

public final class RegexPatterns {

    private RegexPatterns() {
    }

    // Bradfd-230920-13CD0110923-T20227418-BARKER-OLIVIA-ORIG
    public static final Pattern PATTERN_1 = Pattern.compile(
        "^(?:S28[_\\s-]+)?(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?:\\d{5})?" 
        + "(?:[A-Za-z]+[-_\\s]+)?"  
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?(?:-\\d{4})?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG)?(?<versionNumber>\\d*)?"  
        + "(?:[A-Za-z0-9]+)?"
        + "(?:\\.(?i)(mp4|raw))?$"  
    );
    
    // Cardif-230921-61NC0066922-PERKINS-PERKINS-Jaymee-ORIG
    public static final Pattern PATTERN_4 = Pattern.compile(
        "^(?<court>[A-Za-z]+)[-_\\s]*"  
        + "(?<date>\\d{6})[-_\\s]*"    
        + "(?<urn>[A-Za-z0-9]+[-_\\s][A-Za-z0-9]+)?[-_\\s]*" 
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"    
        + "(?<defendantLastName>[A-Za-z]+)[-_\\s]*"    
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"   
        + "(?<versionType>ORIG|COPY|CPY|ORG)?(?<versionNumber>\\d*)?"
        + "(?:\\.(?i)(mp4|raw|RAW))?$"                 
    );

    // Chestr-230925-07EZ1336820-T20217152-Matturie-Elle-Mae-ORIG.mp4
    public static final Pattern PATTERN_8 = Pattern.compile(
        "^(?<court>[A-Za-z]+)?[-_\\s]*"
        + "(?<date>(\\d{6}))?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+[-_\\s][A-Za-z0-9]+)?[-_\\s]*" 
        + "(?<exhibitRef>[A-Za-z0-9]+)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?(?<versionNumber>\\d*)?"
        + "(?:\\.(?i)(mp4|raw))$"
    );
}
