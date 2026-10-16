package catlink

import "time"

// CatUserFollow 用户关注
type CatUserFollow struct {
	ID        uint      `json:"-" gorm:"primarykey"`
	UserID    uint      `json:"-" gorm:"uniqueIndex:idx_user_follow"`
	TargetID  uint      `json:"-" gorm:"uniqueIndex:idx_user_follow"`
	CreatedAt time.Time `json:"-"`
}

// CatShare 公开分享快照
type CatShare struct {
	Code          string    `json:"code" gorm:"primaryKey;size:20"`
	OwnerID       uint      `json:"-" gorm:"index"`
	CatID         string    `json:"catId" gorm:"size:100"`
	Nickname      string    `json:"nickname" gorm:"size:80"`
	Story         string    `json:"story" gorm:"type:text"`
	Gender        string    `json:"gender" gorm:"size:20"`
	AgeBand       string    `json:"ageBand" gorm:"size:20"`
	Coat          string    `json:"coat" gorm:"size:60"`
	Tags          string    `json:"personalityTags" gorm:"size:300"`
	Health        string    `json:"healthSummary" gorm:"size:300"`
	Status        string    `json:"statusCode" gorm:"size:30"`
	ArchiveReason string    `json:"archiveReason" gorm:"size:20"`
	CoverURL      string    `json:"coverUrl" gorm:"size:500"`
	PhotoURLs     string    `json:"photoUrls" gorm:"type:text"`
	CreatedAt     time.Time `json:"-"`
}

// CatDeviceSession App 登录设备会话
type CatDeviceSession struct {
	ID               string     `json:"id" gorm:"primaryKey;size:64"`
	UserID           uint       `json:"-" gorm:"index"`
	Name             string     `json:"name" gorm:"size:120"`
	TokenHash        string     `json:"-" gorm:"size:64;index"` // 兼容旧版本数据，迁移后不再作为认证依据
	AccessTokenHash  string     `json:"-" gorm:"size:64;index"`
	RefreshTokenHash string     `json:"-" gorm:"size:64;index"`
	FamilyID         string     `json:"-" gorm:"size:64;index"`
	CreatedAt        time.Time  `json:"createdAt"`
	LastSeenAt       time.Time  `json:"lastSeenAt"`
	RevokedAt        *time.Time `json:"-"`
}

// CatAccountSecurity 保存 App 账号的邮箱验证状态；与 GVA 管理员账号安全策略隔离。
type CatAccountSecurity struct {
	UserID        uint       `json:"userId" gorm:"primaryKey"`
	EmailVerified bool       `json:"emailVerified" gorm:"default:false;index"`
	VerifiedAt    *time.Time `json:"verifiedAt"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
}

// CatAuthCode 是注册验证和找回密码共用的一次性邮箱验证码。
type CatAuthCode struct {
	ID         uint       `json:"-" gorm:"primaryKey"`
	Email      string     `json:"-" gorm:"size:191;index:idx_cat_auth_code_lookup"`
	Purpose    string     `json:"-" gorm:"size:32;index:idx_cat_auth_code_lookup"`
	CodeHash   string     `json:"-" gorm:"size:100"`
	Attempts   int        `json:"-" gorm:"default:0"`
	ExpiresAt  time.Time  `json:"-" gorm:"index"`
	ConsumedAt *time.Time `json:"-"`
	CreatedAt  time.Time  `json:"-"`
}

// CatEmailConfig 配置业务邮件；真实 SMTP 密钥只从环境变量读取。
type CatEmailConfig struct {
	ID               uint      `json:"id" gorm:"primaryKey"`
	Enabled          bool      `json:"enabled" gorm:"default:false"`
	Host             string    `json:"host" gorm:"size:255"`
	Port             int       `json:"port" gorm:"default:465"`
	From             string    `json:"from" gorm:"size:191"`
	Username         string    `json:"username" gorm:"size:191"`
	PasswordEnv      string    `json:"passwordEnv" gorm:"size:120"`
	Nickname         string    `json:"nickname" gorm:"size:80"`
	TLSMode          string    `json:"tlsMode" gorm:"size:20;default:implicit"`
	LoginOTPRequired bool      `json:"-" gorm:"default:false"` // 兼容旧库列；App 登录不再使用邮箱 OTP。
	UpdatedBy        uint      `json:"updatedBy" gorm:"index"`
	CreatedAt        time.Time `json:"createdAt"`
	UpdatedAt        time.Time `json:"updatedAt"`
}

// CatEncouragement 一言
type CatEncouragement struct {
	ID        uint       `json:"id" gorm:"primarykey"`
	Quote     string     `json:"quote" gorm:"size:500;comment:一句话"`
	Source    string     `json:"source" gorm:"size:100;comment:来源"`
	Enabled   bool       `json:"enabled" gorm:"default:true;comment:是否启用"`
	CreatedAt time.Time  `json:"createdAt"`
	DeletedAt *time.Time `json:"-" gorm:"index"`
}

// CatEncouragementAIConfig 首页一言的 OpenAI 兼容服务配置；密钥仅保存环境变量名。
type CatEncouragementAIConfig struct {
	ID                uint      `json:"id" gorm:"primarykey"`
	Enabled           bool      `json:"enabled" gorm:"default:false"`
	Endpoint          string    `json:"endpoint" gorm:"size:500"`
	Model             string    `json:"model" gorm:"size:120"`
	APIKeyEnv         string    `json:"apiKeyEnv" gorm:"size:120"`
	SystemPrompt      string    `json:"systemPrompt" gorm:"size:1200"`
	Temperature       float64   `json:"temperature" gorm:"default:0.8"`
	TimeoutSeconds    int       `json:"timeoutSeconds" gorm:"default:20"`
	LastQuote         string    `json:"lastQuote" gorm:"size:500"`
	LastGeneratedDate string    `json:"lastGeneratedDate" gorm:"size:10"`
	UpdatedBy         uint      `json:"updatedBy" gorm:"index"`
	CreatedAt         time.Time `json:"createdAt"`
	UpdatedAt         time.Time `json:"updatedAt"`
}

// CatPublicCat 公开猫咪（白名单脱敏后对外可见）
type CatPublicCat struct {
	ID            uint      `json:"id" gorm:"primarykey"`
	OwnerID       uint      `json:"ownerId" gorm:"uniqueIndex:idx_owner_local_cat;index:idx_cat_public_owner_state_updated,priority:1;comment:创建人"`
	LocalID       string    `json:"localId" gorm:"size:100;uniqueIndex:idx_owner_local_cat;comment:App本地档案ID"`
	Nickname      string    `json:"nickname" gorm:"size:80;comment:公开昵称"`
	Alias         string    `json:"alias" gorm:"size:80"`
	Story         string    `json:"story" gorm:"type:text;comment:公开故事"`
	Gender        string    `json:"gender" gorm:"size:20"`
	AgeBand       string    `json:"ageBand" gorm:"size:20"`
	Coat          string    `json:"coat" gorm:"size:60"`
	Personality   string    `json:"personalityTags" gorm:"size:500"`
	HealthSummary string    `json:"healthSummary" gorm:"size:500"`
	Weight        string    `json:"weight" gorm:"size:40"`
	Diet          string    `json:"dietRestrictions" gorm:"size:500"`
	HumanContact  string    `json:"contactWithHumans" gorm:"size:500"`
	CatContact    string    `json:"contactWithCats" gorm:"size:500"`
	CatStatus     string    `json:"catStatus" gorm:"size:30"`
	StatusReason  string    `json:"statusReason" gorm:"size:500"`
	ArchiveReason string    `json:"archiveReason" gorm:"size:20;index"`
	LegacyStatus  string    `json:"legacyStatus,omitempty" gorm:"size:80;comment:升级前无法识别的状态"`
	RescuedDate   int64     `json:"rescuedDateEpochDay"`
	StatusDate    *int64    `json:"statusDateEpochDay"`
	CityCode      string    `json:"cityCode" gorm:"size:20"`
	DistrictCode  string    `json:"districtCode" gorm:"size:20"`
	Status        string    `json:"status" gorm:"size:20;default:adoptable;index;index:idx_cat_public_state_status_updated,priority:2;comment:rescue/treating/adoptable/adopted/archived"`
	State         string    `json:"state" gorm:"size:20;default:published;index:idx_cat_public_state_status_updated,priority:1;index:idx_cat_public_owner_state_updated,priority:2;comment:published/paused/archived"`
	CoverURL      string    `json:"coverUrl" gorm:"size:500"`
	PhotoURLs     string    `json:"photoUrls" gorm:"type:text"`
	CoverMediaID  uint      `json:"coverMediaId" gorm:"index"`
	PhotoMediaIDs string    `json:"photoMediaIds" gorm:"type:text"`
	LocationName  string    `json:"locationName" gorm:"size:120;comment:地区文字"`
	FollowCount   int       `json:"followCount" gorm:"default:0"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt" gorm:"index:idx_cat_public_state_status_updated,priority:3;index:idx_cat_public_owner_state_updated,priority:3"`
	AdoptedByName string    `json:"adoptedByName" gorm:"size:80"`
}

// CatPost 社区帖子
type CatPost struct {
	ID           uint      `json:"id" gorm:"primarykey"`
	AuthorID     uint      `json:"authorId" gorm:"index;index:idx_cat_posts_author_created,priority:1"`
	ClientID     *string   `json:"clientId" gorm:"size:36;uniqueIndex:idx_cat_post_author_client"`
	Title        string    `json:"title" gorm:"size:200"`
	Content      string    `json:"content" gorm:"type:text"`
	Topic        string    `json:"topic" gorm:"size:20;default:all;comment:all/help/adopt/exchange"`
	CatRef       string    `json:"catRef" gorm:"size:100"`
	ImageURL     string    `json:"imageUrl" gorm:"type:longtext"`
	MediaIDs     string    `json:"mediaIds" gorm:"type:text"`
	CityCode     string    `json:"cityCode" gorm:"size:80;index"`
	DistrictCode string    `json:"districtCode" gorm:"size:80;index"`
	LikeCount    int       `json:"likeCount" gorm:"default:0"`
	CommentCount int       `json:"commentCount" gorm:"default:0"`
	Locked       bool      `json:"locked" gorm:"default:false;index:idx_cat_posts_locked_created,priority:1;comment:锁定(管理)"`
	CreatedAt    time.Time `json:"createdAt" gorm:"index;index:idx_cat_posts_author_created,priority:2;index:idx_cat_posts_locked_created,priority:2"`
}

// CatComment 评论
type CatComment struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	PostID    uint      `json:"postId" gorm:"index;index:idx_cat_comments_post_created,priority:1"`
	AuthorID  uint      `json:"authorId" gorm:"index;index:idx_cat_comments_author_created,priority:1"`
	Body      string    `json:"body" gorm:"type:text"`
	ReplyToID uint      `json:"replyToId" gorm:"index"`
	LikeCount int       `json:"likeCount" gorm:"default:0"`
	CreatedAt time.Time `json:"createdAt" gorm:"index:idx_cat_comments_post_created,priority:2;index:idx_cat_comments_author_created,priority:2"`
}

type CatCommentLike struct {
	ID        uint `gorm:"primarykey"`
	UserID    uint `gorm:"uniqueIndex:idx_comment_like_user_comment"`
	CommentID uint `gorm:"uniqueIndex:idx_comment_like_user_comment"`
	CreatedAt time.Time
}

// CatLike 点赞
type CatLike struct {
	ID        uint `gorm:"primarykey"`
	UserID    uint `gorm:"uniqueIndex:idx_cat_like_user_post"`
	PostID    uint `gorm:"uniqueIndex:idx_cat_like_user_post"`
	CreatedAt time.Time
}

// CatBookmark 收藏
type CatBookmark struct {
	ID        uint `gorm:"primarykey"`
	UserID    uint `gorm:"uniqueIndex:idx_cat_bookmark_user_post"`
	PostID    uint `gorm:"uniqueIndex:idx_cat_bookmark_user_post"`
	CreatedAt time.Time
}

// CatFollow 公开猫关注
type CatFollow struct {
	ID        uint `gorm:"primarykey"`
	UserID    uint `gorm:"uniqueIndex:idx_cat_follow_user_cat"`
	CatID     uint `gorm:"uniqueIndex:idx_cat_follow_user_cat"`
	CreatedAt time.Time
}

// CatReport 举报
type CatReport struct {
	ID         uint      `json:"id" gorm:"primarykey"`
	Reporter   uint      `json:"reporter" gorm:"index:idx_cat_reports_reporter_created,priority:1"`
	TargetType string    `json:"targetType" gorm:"size:20;comment:post/comment/user/cat"`
	TargetID   string    `json:"targetId"`
	Reason     string    `json:"reason" gorm:"size:500"`
	Status     string    `json:"status" gorm:"size:20;default:pending"`
	CreatedAt  time.Time `json:"createdAt" gorm:"index:idx_cat_reports_reporter_created,priority:2"`
}

type CatModerationAction struct {
	ID         uint      `json:"id" gorm:"primarykey"`
	ReportID   uint      `json:"reportId" gorm:"index"`
	TargetType string    `json:"targetType" gorm:"size:20;index"`
	TargetID   string    `json:"targetId" gorm:"size:100;index"`
	Action     string    `json:"action" gorm:"size:24"`
	Reason     string    `json:"reason" gorm:"size:500"`
	OperatorID uint      `json:"operatorId" gorm:"index"`
	Status     string    `json:"status" gorm:"size:16;default:active;index"`
	CreatedAt  time.Time `json:"createdAt"`
}

type CatModerationAppeal struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	ActionID  uint      `json:"actionId" gorm:"index"`
	UserID    uint      `json:"userId" gorm:"index"`
	Reason    string    `json:"reason" gorm:"size:1000"`
	Status    string    `json:"status" gorm:"size:16;default:pending;index"`
	Result    string    `json:"result" gorm:"size:500"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

// CatAd 广告
type CatAd struct {
	ID          uint       `json:"id" gorm:"primarykey"`
	Slot        string     `json:"slot" gorm:"size:20;comment:launch/community"`
	Name        string     `json:"name" gorm:"size:120"`
	ImageURL    string     `json:"imageUrl" gorm:"size:500"`
	LinkURL     string     `json:"linkUrl" gorm:"size:500"`
	Fee         string     `json:"fee" gorm:"size:60"`
	OnTime      *time.Time `json:"onTime"`
	Duration    string     `json:"duration" gorm:"size:120"`
	OffTime     *time.Time `json:"offTime"`
	Contact     string     `json:"contact" gorm:"size:120"`
	HeightMode  string     `json:"heightMode" gorm:"size:16;default:ratio;comment:fixed/ratio"`
	HeightValue float64    `json:"heightValue" gorm:"default:42.857;comment:fixed=px,ratio=宽度百分比"`
	Enabled     bool       `json:"enabled" gorm:"default:false"`
	CreatedAt   time.Time  `json:"createdAt"`
}

// CatVersion 版本
type CatVersion struct {
	ID          uint      `json:"id" gorm:"primarykey"`
	VersionCode int       `json:"versionCode"`
	VersionName string    `json:"versionName" gorm:"size:30"`
	Content     string    `json:"content" gorm:"type:text"`
	DownloadURL string    `json:"downloadUrl" gorm:"size:500"`
	CreatedAt   time.Time `json:"createdAt"`
}

// CatNotification 通知
type CatNotification struct {
	ID         uint      `json:"id" gorm:"primarykey"`
	CampaignID uint      `json:"campaignId" gorm:"index"`
	ToUser     uint      `json:"toUser" gorm:"index;index:idx_cat_notifications_user_created,priority:1"`
	Type       string    `json:"type" gorm:"size:20;comment:announcement/remind/private/report"`
	Title      string    `json:"title" gorm:"size:120"`
	Body       string    `json:"body" gorm:"type:text"`
	Link       string    `json:"link" gorm:"size:300"`
	ReadFlag   bool      `json:"read" gorm:"default:false"`
	CreatedAt  time.Time `json:"createdAt" gorm:"index;index:idx_cat_notifications_user_created,priority:2"`
}

// CatAnnouncementCampaign records segmented, scheduled and retractable announcements.
type CatAnnouncementCampaign struct {
	ID             uint       `json:"id" gorm:"primarykey"`
	Title          string     `json:"title" gorm:"size:120"`
	Body           string     `json:"body" gorm:"type:text"`
	Link           string     `json:"link" gorm:"size:300"`
	SegmentType    string     `json:"segmentType" gorm:"size:24;default:all;index"`
	SegmentValue   string     `json:"segmentValue" gorm:"size:120"`
	Status         string     `json:"status" gorm:"size:16;default:scheduled;index"`
	ScheduledAt    time.Time  `json:"scheduledAt" gorm:"index"`
	SentAt         *time.Time `json:"sentAt"`
	WithdrawnAt    *time.Time `json:"withdrawnAt"`
	AudienceCount  int        `json:"audienceCount"`
	DeliveredCount int        `json:"deliveredCount"`
	ReadCount      int        `json:"readCount"`
	CreatedBy      uint       `json:"createdBy" gorm:"index"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

// CatApply 领养申请
type CatApply struct {
	ID             uint       `json:"id" gorm:"primarykey"`
	CatID          uint       `json:"catId" gorm:"index:idx_cat_apply_cat_status"`
	ApplicantID    uint       `json:"applicantId" gorm:"index:idx_cat_apply_applicant_status"`
	OwnerID        uint       `json:"ownerId" gorm:"index:idx_cat_apply_owner_status"`
	Message        string     `json:"message" gorm:"type:text"`
	ContactSummary string     `json:"contactSummary" gorm:"size:200"`
	Status         string     `json:"status" gorm:"size:20;default:pending;index:idx_cat_apply_cat_status;index:idx_cat_apply_applicant_status;index:idx_cat_apply_owner_status"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
	DecidedAt      *time.Time `json:"decidedAt"`
}

// CatConversation 私聊会话
type CatConversation struct {
	ID          uint      `json:"id" gorm:"primarykey"`
	UserA       uint      `json:"userA" gorm:"index"`
	UserB       uint      `json:"userB" gorm:"index"`
	LastMessage string    `json:"lastMessage" gorm:"size:500"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// CatMessage 私聊消息
type CatMessage struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	ConvID    uint      `json:"convId" gorm:"index;index:idx_cat_messages_conv_created,priority:1"`
	SenderID  uint      `json:"senderId" gorm:"index:idx_cat_messages_sender_created,priority:1"`
	Body      string    `json:"body" gorm:"type:text"`
	ReadFlag  bool      `json:"read" gorm:"default:false"`
	CreatedAt time.Time `json:"createdAt" gorm:"index:idx_cat_messages_conv_created,priority:2;index:idx_cat_messages_sender_created,priority:2"`
}

// CatMediaUploadWindow 账号级上传尝试预算；在读取请求体前原子预留。
type CatMediaUploadWindow struct {
	ID           uint      `json:"-" gorm:"primarykey"`
	OwnerID      uint      `json:"-" gorm:"uniqueIndex:idx_cat_media_upload_window"`
	WindowStart  time.Time `json:"-" gorm:"uniqueIndex:idx_cat_media_upload_window"`
	AttemptCount int       `json:"-"`
	RequestBytes int64     `json:"-"`
	UpdatedAt    time.Time `json:"-"`
}

// CatMedia 媒体
type CatMedia struct {
	ID              uint       `json:"id" gorm:"primarykey"`
	OwnerID         uint       `json:"ownerId" gorm:"index;index:idx_cat_media_owner_created,priority:1"`
	Category        string     `json:"category" gorm:"size:20;comment:cat/contract/avatar/post/misc"`
	URL             string     `json:"url" gorm:"size:500;index"`
	StorageKey      string     `json:"-" gorm:"size:500;index"`
	StorageProvider string     `json:"storageProvider" gorm:"size:32;index;default:local"`
	Temporary       bool       `json:"-" gorm:"default:false;index"`
	ExpiresAt       *time.Time `json:"-" gorm:"index"`
	Size            int64      `json:"size"`
	CreatedAt       time.Time  `json:"createdAt" gorm:"index:idx_cat_media_owner_created,priority:2"`
}

// CatVault E2EE vault 同步记录（服务端 opaque）
type CatVault struct {
	ID             uint   `gorm:"primarykey"`
	AccountID      uint   `gorm:"index;uniqueIndex:idx_cat_vault_entity"`
	EntityType     string `gorm:"size:40;uniqueIndex:idx_cat_vault_entity"`
	EntityID       string `gorm:"size:100;uniqueIndex:idx_cat_vault_entity"`
	Revision       int64  `gorm:"default:1"`
	ServerRevision int64  `gorm:"index;default:0"`
	KeyVersion     int    `gorm:"default:1"`
	Tombstone      bool   `gorm:"default:false"`
	Payload        string `json:"-" gorm:"type:longtext"`
	UpdatedAt      time.Time
}

// CatVaultMeta
type CatVaultMeta struct {
	ID           uint  `gorm:"primarykey"`
	AccountID    uint  `gorm:"uniqueIndex"`
	Revision     int64 `gorm:"default:0"`
	KeyVersion   int   `gorm:"default:1"`
	RecordCount  int64 `gorm:"default:0"`
	PayloadBytes int64 `gorm:"default:0"`
	UpdatedAt    time.Time
}

// CatSafetyRelation 用户对其他用户的屏蔽关系。
type CatSafetyRelation struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	UserID    uint      `json:"userId" gorm:"uniqueIndex:idx_cat_safety_user_target_kind"`
	TargetID  uint      `json:"targetId" gorm:"uniqueIndex:idx_cat_safety_user_target_kind"`
	Kind      string    `json:"kind" gorm:"size:12;uniqueIndex:idx_cat_safety_user_target_kind"`
	CreatedAt time.Time `json:"createdAt"`
}

// CatTimelineEvent 公开猫咪动态，只允许发布者主动公开的摘要和照片。
type CatTimelineEvent struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	CatID     uint      `json:"catId" gorm:"index:idx_cat_timeline_cat_created"`
	OwnerID   uint      `json:"ownerId" gorm:"index"`
	Kind      string    `json:"kind" gorm:"size:24"`
	Body      string    `json:"body" gorm:"type:text"`
	MediaURLs string    `json:"mediaUrls" gorm:"type:text"`
	MediaIDs  string    `json:"mediaIds" gorm:"type:text"`
	Public    bool      `json:"public" gorm:"default:true;index"`
	CreatedAt time.Time `json:"createdAt" gorm:"index:idx_cat_timeline_cat_created"`
}

// CatAdoptionEvent 领养申请状态和补充材料的审计时间线。
type CatAdoptionEvent struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	ApplyID   uint      `json:"applyId" gorm:"index"`
	ActorID   uint      `json:"actorId" gorm:"index"`
	Status    string    `json:"status" gorm:"size:24"`
	Note      string    `json:"note" gorm:"type:text"`
	MediaURLs string    `json:"mediaUrls" gorm:"type:text"`
	MediaIDs  string    `json:"mediaIds" gorm:"type:text"`
	CreatedAt time.Time `json:"createdAt" gorm:"index"`
}

// CatMissingCase 失踪猫协作事件，坐标只保存模糊网格。
type CatMissingCase struct {
	ID          uint       `json:"id" gorm:"primarykey"`
	CatID       uint       `json:"catId" gorm:"index"`
	OwnerID     uint       `json:"ownerId" gorm:"index"`
	Latitude    float64    `json:"latitudeGrid"`
	Longitude   float64    `json:"longitudeGrid"`
	RadiusKM    float64    `json:"radiusKm"`
	Description string     `json:"description" gorm:"type:text"`
	Status      string     `json:"status" gorm:"size:16;default:open;index;index:idx_cat_missing_status_created,priority:1"`
	CreatedAt   time.Time  `json:"createdAt" gorm:"index:idx_cat_missing_status_created,priority:2"`
	ClosedAt    *time.Time `json:"closedAt"`
}

type CatMissingSighting struct {
	ID         uint      `json:"id" gorm:"primarykey"`
	CaseID     uint      `json:"caseId" gorm:"index"`
	ReporterID uint      `json:"reporterId" gorm:"index"`
	Latitude   float64   `json:"latitudeGrid"`
	Longitude  float64   `json:"longitudeGrid"`
	Note       string    `json:"note" gorm:"type:text"`
	CreatedAt  time.Time `json:"createdAt"`
}

// CatRescueTask 小团队救助协作任务。
type CatRescueTask struct {
	ID          uint       `json:"id" gorm:"primarykey"`
	CatID       uint       `json:"catId" gorm:"index"`
	OwnerID     uint       `json:"ownerId" gorm:"index"`
	AssigneeID  uint       `json:"assigneeId" gorm:"index"`
	Kind        string     `json:"kind" gorm:"size:24"`
	Note        string     `json:"note" gorm:"type:text"`
	Status      string     `json:"status" gorm:"size:16;default:open;index"`
	DueAt       *time.Time `json:"dueAt"`
	CreatedAt   time.Time  `json:"createdAt"`
	CompletedAt *time.Time `json:"completedAt"`
}

type CatIdentityBadge struct {
	ID          uint      `json:"id" gorm:"primarykey"`
	UserID      uint      `json:"userId" gorm:"uniqueIndex"`
	Kind        string    `json:"kind" gorm:"size:24"`
	DisplayName string    `json:"displayName" gorm:"size:80"`
	Status      string    `json:"status" gorm:"size:16;default:pending;index"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

type CatPushDevice struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	UserID    uint      `json:"userId" gorm:"index"`
	Provider  string    `json:"provider" gorm:"size:24"`
	TokenHash string    `json:"-" gorm:"size:64;uniqueIndex"`
	Enabled   bool      `json:"enabled" gorm:"default:true;index"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type CatNotificationOutbox struct {
	ID             uint       `json:"id" gorm:"primarykey"`
	NotificationID uint       `json:"notificationId" gorm:"index"`
	Provider       string     `json:"provider" gorm:"size:24"`
	Status         string     `json:"status" gorm:"size:16;default:pending;index"`
	Attempts       int        `json:"attempts"`
	NextAttemptAt  *time.Time `json:"nextAttemptAt"`
	LastError      string     `json:"lastError" gorm:"size:500"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

type CatStorageMigrationJob struct {
	ID           uint       `json:"id" gorm:"primarykey"`
	ActorID      uint       `json:"actorId" gorm:"index"`
	FromProvider string     `json:"fromProvider" gorm:"size:32"`
	ToProvider   string     `json:"toProvider" gorm:"size:32"`
	Cursor       uint       `json:"cursor"`
	Copied       int64      `json:"copied"`
	Verified     int64      `json:"verified"`
	Status       string     `json:"status" gorm:"size:16;default:queued;index"`
	LastError    string     `json:"lastError" gorm:"size:500"`
	CreatedAt    time.Time  `json:"createdAt"`
	UpdatedAt    time.Time  `json:"updatedAt"`
	FinishedAt   *time.Time `json:"finishedAt"`
}

type CatRetentionPolicy struct {
	ID        uint      `json:"id" gorm:"primarykey"`
	Kind      string    `json:"kind" gorm:"size:32;uniqueIndex"`
	Days      int       `json:"days"`
	Enabled   bool      `json:"enabled" gorm:"default:true"`
	UpdatedAt time.Time `json:"updatedAt"`
}
