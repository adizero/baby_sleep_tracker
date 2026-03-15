package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Milestone(
    val ageLabel: String,
    val ageDays: Int,
    val emoji: String,
    val category: String,
    val title: String,
    val description: String
)

private val categoryColors = mapOf(
    "Motor" to Color(0xFF4CAF50),
    "Vision" to Color(0xFF2196F3),
    "Sleep" to Color(0xFF673AB7),
    "Feeding" to Color(0xFFFF9800),
    "Teeth" to Color(0xFFE91E63),
    "Social" to Color(0xFFFF5722),
    "Language" to Color(0xFF009688),
    "Cognitive" to Color(0xFF795548),
    "Fun Fact" to Color(0xFF607D8B)
)

private val milestones = listOf(
    // Newborn (0-2 weeks)
    Milestone("Newborn", 0, "👶", "Vision", "Seeing the world", "Can only see 8-12 inches away — just enough to see your face while feeding. Everything beyond is a beautiful blur."),
    Milestone("Newborn", 0, "😴", "Sleep", "Sleep champion", "Sleeps 16-17 hours a day in short bursts of 2-4 hours. Day and night? What's the difference!"),
    Milestone("Newborn", 0, "🤲", "Motor", "Reflexes rule", "Born with over 70 reflexes! The grasp reflex is so strong a newborn could hang from a bar (don't try this)."),
    Milestone("Newborn", 0, "🧠", "Fun Fact", "Big brain energy", "Baby's brain is 25% of adult size at birth, but will double in the first year."),
    Milestone("Newborn", 0, "👃", "Social", "Super sniffer", "Can recognize mom's scent within hours of birth. Your smell is their favorite smell in the world."),

    // 2 weeks
    Milestone("2 weeks", 14, "👀", "Vision", "Focusing power", "Starting to focus on high-contrast patterns — black and white images are fascinating!"),
    Milestone("2 weeks", 14, "🍼", "Feeding", "Feeding pro", "Stomach has grown from cherry-size to walnut-size. Feeds 8-12 times per day."),

    // 1 month
    Milestone("1 month", 30, "👁️", "Vision", "Face fan", "Prefers looking at faces over any other pattern. Your face is the most interesting thing in the room."),
    Milestone("1 month", 30, "💪", "Motor", "Tiny lifts", "Can briefly lift head during tummy time. Those neck muscles are getting a workout!"),
    Milestone("1 month", 30, "😴", "Sleep", "Longer stretches", "May start sleeping in 3-4 hour stretches. The light at the end of the tunnel!"),
    Milestone("1 month", 30, "🗣️", "Language", "First coos", "Starts making soft cooing and gurgling sounds. The beginning of conversation!"),
    Milestone("1 month", 30, "🧠", "Fun Fact", "Taste buds galore", "Has about 10,000 taste buds — three times more than adults! They're even on the roof of the mouth."),

    // 6 weeks
    Milestone("6 weeks", 42, "😊", "Social", "First real smile", "The magical first social smile appears! Not gas this time — a real response to seeing you."),
    Milestone("6 weeks", 42, "👀", "Vision", "Color vision emerging", "Starting to distinguish red from green. The world is getting more colorful!"),
    Milestone("6 weeks", 42, "😴", "Sleep", "Peak fussiness", "Crying peaks around 6-8 weeks, then gradually decreases. Hang in there!"),

    // 2 months
    Milestone("2 months", 60, "👀", "Vision", "Tracking objects", "Can follow a moving object with their eyes in a smooth arc. Try slowly moving a toy!"),
    Milestone("2 months", 60, "💪", "Motor", "Head control", "Holds head at 45° during tummy time. Getting stronger every day!"),
    Milestone("2 months", 60, "🗣️", "Language", "Vowel sounds", "Makes \"ah\" and \"oh\" sounds. First attempts at vowels — adorable tiny conversations."),
    Milestone("2 months", 60, "😴", "Sleep", "Day-night sorting", "Starting to understand that daytime is for being awake and night is for sleeping."),
    Milestone("2 months", 60, "🧠", "Fun Fact", "Kneecap mystery", "Born without kneecaps! They develop between 3-5 years old. Babies have cartilage instead."),

    // 3 months
    Milestone("3 months", 90, "🤲", "Motor", "Reaching and batting", "Starts reaching for and batting at dangling toys. Hand-eye coordination is kicking in!"),
    Milestone("3 months", 90, "👀", "Vision", "Color vision complete", "Can now see the full color spectrum. The world just went from blurry grayscale to HD color!"),
    Milestone("3 months", 90, "😄", "Social", "Belly laughs", "First real laughs appear! One of the most delightful sounds you'll ever hear."),
    Milestone("3 months", 90, "😴", "Sleep", "Settling into routine", "Many babies develop a more predictable sleep pattern. Total sleep: about 14-16 hours."),
    Milestone("3 months", 90, "🍼", "Feeding", "Efficient feeder", "Breastfeeding sessions get shorter and more efficient. Baby is a pro now!"),
    Milestone("3 months", 90, "🧠", "Fun Fact", "Rapid growth", "Baby has gained about 2 kg since birth. The brain has grown by 64%!"),

    // 4 months
    Milestone("4 months", 120, "🤲", "Motor", "Grasping objects", "Can grab toys and bring them to mouth. Everything becomes a taste test!"),
    Milestone("4 months", 120, "😴", "Sleep", "Sleep regression", "The dreaded 4-month sleep regression! Sleep cycles mature — temporary disruption is normal."),
    Milestone("4 months", 120, "👀", "Vision", "Depth perception", "Developing depth perception. Can tell how far away objects are. Drop test: activated!"),
    Milestone("4 months", 120, "🗣️", "Language", "Babbling begins", "Starts babbling chains of consonant-vowel combos: \"ba-ba\", \"ma-ma\" (not meaning mom yet!)."),
    Milestone("4 months", 120, "🧠", "Fun Fact", "Mirror, mirror", "Fascinated by mirrors but doesn't recognize themselves yet — thinks it's another baby!"),

    // 5 months
    Milestone("5 months", 150, "💪", "Motor", "Rolling over", "Can roll from tummy to back, and some from back to tummy. No more leaving baby on the bed!"),
    Milestone("5 months", 150, "🦷", "Teeth", "Teething signs", "Drooling increases, gums may be swollen. First teeth could appear anytime now!"),
    Milestone("5 months", 150, "😊", "Social", "Recognizes name", "Starts turning when they hear their name. You exist to them as a concept!"),
    Milestone("5 months", 150, "🧠", "Fun Fact", "Object permanence", "Beginning to understand that things exist even when hidden. Peek-a-boo becomes mind-blowing!"),

    // 6 months
    Milestone("6 months", 180, "💪", "Motor", "Sitting up", "Can sit with support, some without. A whole new perspective on the world!"),
    Milestone("6 months", 180, "🍼", "Feeding", "Ready for solids", "Shows signs of readiness for solid food: good head control, interest in food, loss of tongue-thrust reflex."),
    Milestone("6 months", 180, "🦷", "Teeth", "First tooth!", "First tooth often appears — usually a bottom front incisor. Watch your fingers during feeding!"),
    Milestone("6 months", 180, "😴", "Sleep", "Longer nights", "Many babies can sleep 6-8 hour stretches at night. Some even sleep through!"),
    Milestone("6 months", 180, "🗣️", "Language", "Responding to tone", "Understands emotional tone — knows when you're happy, sad, or stern by voice alone."),
    Milestone("6 months", 180, "👀", "Vision", "Near adult vision", "Vision is close to 20/20 now. Can see across the room clearly and track fast-moving objects."),
    Milestone("6 months", 180, "🧠", "Fun Fact", "Growth spurt", "Birth weight has likely doubled! Brain is now about 50% of adult size."),

    // 7 months
    Milestone("7 months", 210, "💪", "Motor", "Sitting solo", "Sits independently without support. Can play with toys while sitting — multitasking!"),
    Milestone("7 months", 210, "🤲", "Motor", "Transferring objects", "Can pass objects from one hand to the other. A big cognitive-motor milestone!"),
    Milestone("7 months", 210, "😰", "Social", "Stranger anxiety", "May become wary of unfamiliar faces. This is a sign of healthy attachment!"),

    // 8 months
    Milestone("8 months", 240, "💪", "Motor", "Crawling prep", "Gets into crawling position, rocks back and forth. Almost ready to explore!"),
    Milestone("8 months", 240, "🤲", "Motor", "Pincer grasp emerging", "Starting to pick up small objects between thumb and finger. Cheerios become a workout!"),
    Milestone("8 months", 240, "🗣️", "Language", "Understanding words", "Starts understanding common words like \"no\", \"bye-bye\", and \"mama\"/\"dada\" (in context)."),
    Milestone("8 months", 240, "😴", "Sleep", "Separation anxiety at night", "May resist bedtime due to separation anxiety. Consistent routine helps enormously."),

    // 9 months
    Milestone("9 months", 270, "💪", "Motor", "Crawling!", "Most babies are crawling now — some scoot, some army-crawl, some bear-walk. All are valid!"),
    Milestone("9 months", 270, "🤲", "Motor", "Pincer grasp mastered", "Can precisely pick up tiny objects. Time to baby-proof everything at floor level!"),
    Milestone("9 months", 270, "🗣️", "Language", "Mama and Dada", "May start using \"mama\" and \"dada\" meaningfully — referring to the actual parent!"),
    Milestone("9 months", 270, "🧠", "Cognitive", "Cause and effect", "Drops things on purpose to see what happens. Loves banging objects together. Science!"),
    Milestone("9 months", 270, "🦷", "Teeth", "More teeth arriving", "Upper front teeth often appear. Now has 2-4 teeth for a charming grin."),

    // 10 months
    Milestone("10 months", 300, "💪", "Motor", "Pulling to stand", "Pulls up to standing using furniture. The world looks different from up here!"),
    Milestone("10 months", 300, "🤲", "Motor", "Clapping and waving", "Learns to clap hands and wave bye-bye. Social gestures are blossoming!"),
    Milestone("10 months", 300, "🍼", "Feeding", "Self-feeding skills", "Picks up finger foods and feeds themselves. Messy but important independence!"),

    // 11 months
    Milestone("11 months", 330, "💪", "Motor", "Cruising", "Walks while holding onto furniture — 'cruising'. Steps are getting more confident!"),
    Milestone("11 months", 330, "🗣️", "Language", "First words", "May say 1-3 real words beyond mama/dada. Each word is a tiny breakthrough!"),
    Milestone("11 months", 330, "🧠", "Cognitive", "Following instructions", "Understands simple requests like \"give me the ball\" or \"where's the dog?\""),

    // 12 months
    Milestone("12 months", 365, "💪", "Motor", "First steps!", "Many babies take their first independent steps around now. Walking is coming!"),
    Milestone("12 months", 365, "🎂", "Fun Fact", "What a year!", "Brain is 60% of adult size. Birth weight has tripled. About 200 bones (adults have 206 — some fuse together!)."),
    Milestone("12 months", 365, "😴", "Sleep", "One nap transition begins", "Starting to transition from two naps to one. Total sleep: about 12-14 hours."),
    Milestone("12 months", 365, "🗣️", "Language", "Word explosion prep", "Understands about 50 words even if only saying a few. A vocabulary explosion is coming!"),
    Milestone("12 months", 365, "🦷", "Teeth", "Toothy grin", "Usually has 6-8 teeth now. First molars may start making their painful debut."),
    Milestone("12 months", 365, "🍼", "Feeding", "Cup drinking", "Can drink from a sippy cup and eat most table foods. Goodbye bottles (soon)!"),

    // 15 months
    Milestone("15 months", 456, "💪", "Motor", "Walking confidently", "Most toddlers are walking well. Running (wobbling fast) may start soon!"),
    Milestone("15 months", 456, "🗣️", "Language", "Pointing and naming", "Points at things they want and may name them. Communication becomes intentional."),
    Milestone("15 months", 456, "🧠", "Cognitive", "Stacking blocks", "Can stack 2-3 blocks. Knock them down. Stack again. Best game ever!"),

    // 18 months
    Milestone("18 months", 548, "💪", "Motor", "Running and climbing", "Runs (a bit unsteadily) and loves climbing everything. Turn your back and they're on the table!"),
    Milestone("18 months", 548, "🗣️", "Language", "Word explosion", "Vocabulary explodes — learning multiple new words per day! Can say 10-50 words."),
    Milestone("18 months", 548, "😴", "Sleep", "One nap established", "Most toddlers are solidly on one afternoon nap. Bedtime may get easier."),
    Milestone("18 months", 548, "🧠", "Cognitive", "Pretend play begins", "Starts pretend play — feeding a doll, talking on a toy phone. Imagination is blooming!"),
    Milestone("18 months", 548, "🦷", "Teeth", "Canines arriving", "Canine teeth may start appearing. About 12-16 teeth total now."),
    Milestone("18 months", 548, "😊", "Social", "Mirror recognition", "Finally recognizes themselves in the mirror! Touches their own face, not the reflection's."),

    // 24 months
    Milestone("24 months", 730, "🗣️", "Language", "Two-word sentences", "Combines words: \"more milk\", \"daddy go\", \"big dog\". Grammar is starting!"),
    Milestone("24 months", 730, "💪", "Motor", "Kicking and jumping", "Can kick a ball and attempt to jump with both feet. Physical confidence is soaring!"),
    Milestone("24 months", 730, "🧠", "Cognitive", "Sorting and matching", "Can sort shapes, match colors, complete simple puzzles. Problem-solving is developing!"),
    Milestone("24 months", 730, "😊", "Social", "Parallel play", "Plays alongside other children. True interactive play comes next!"),
    Milestone("24 months", 730, "🎂", "Fun Fact", "Two years!", "Brain is 75% of adult size. Has about 100 trillion neural connections — more than an adult!"),
    Milestone("24 months", 730, "🦷", "Teeth", "Almost complete", "Second molars arriving. By age 2.5-3, all 20 baby teeth should be in!"),

    // 30 months
    Milestone("30 months", 913, "🗣️", "Language", "Sentences and stories", "Speaks in 3-5 word sentences. Can tell you about their day (sort of)!"),
    Milestone("30 months", 913, "💪", "Motor", "Stairs and pedals", "Goes up and down stairs with alternating feet. May start pedaling a tricycle!"),
    Milestone("30 months", 913, "🧠", "Cognitive", "Counting begins", "Starts counting objects (1, 2, 3...) and recognizing some letters and numbers."),

    // 36 months
    Milestone("36 months", 1095, "🗣️", "Language", "Full conversations", "Speaks in complex sentences, asks endless \"why?\" questions. Welcome to the why phase!"),
    Milestone("36 months", 1095, "💪", "Motor", "Balance and coordination", "Stands on one foot, walks on tiptoes, catches a ball. Physical skills are remarkable!"),
    Milestone("36 months", 1095, "🧠", "Cognitive", "Imagination flourishes", "Rich pretend play with elaborate scenarios. Imaginary friends may appear!"),
    Milestone("36 months", 1095, "😊", "Social", "Cooperative play", "Starts playing WITH other children, taking turns, sharing (sometimes). Social skills are blooming!"),
    Milestone("36 months", 1095, "🎂", "Fun Fact", "Three years!", "Brain is 80% of adult size. Has learned about 1,000 words. Can ride a tricycle and draw a circle!")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MilestonesScreen(
    birthDate: LocalDate?,
    babyName: String?,
    onBack: () -> Unit
) {
    val ageDays = birthDate?.let {
        ChronoUnit.DAYS.between(it, LocalDate.now()).toInt().coerceAtLeast(0)
    }

    // Group milestones by age label, maintaining order
    val groupedMilestones = remember {
        milestones.groupBy { it.ageLabel }
            .entries
            .sortedBy { entry -> entry.value.first().ageDays }
            .toList()
    }

    // Find the index of the current age group to scroll to
    val currentGroupIndex = remember(ageDays) {
        if (ageDays == null) 0
        else {
            val idx = groupedMilestones.indexOfLast { (_, items) ->
                items.first().ageDays <= ageDays
            }
            if (idx >= 0) idx else 0
        }
    }

    // Build flat list items for LazyColumn: headers + milestone cards
    data class ListItem(
        val isHeader: Boolean,
        val ageLabel: String = "",
        val ageDays: Int = 0,
        val milestone: Milestone? = null,
        val isCurrent: Boolean = false
    )

    val listItems = remember(ageDays) {
        val items = mutableListOf<ListItem>()
        for ((label, groupMilestones) in groupedMilestones) {
            val groupAgeDays = groupMilestones.first().ageDays
            val isCurrent = ageDays != null && groupedMilestones.let { groups ->
                val currentIdx = groups.indexOfFirst { it.key == label }
                val nextIdx = currentIdx + 1
                if (nextIdx < groups.size) {
                    ageDays >= groupAgeDays && ageDays < groups[nextIdx].value.first().ageDays
                } else {
                    ageDays >= groupAgeDays
                }
            }
            items.add(ListItem(isHeader = true, ageLabel = label, ageDays = groupAgeDays, isCurrent = isCurrent))
            for (m in groupMilestones) {
                items.add(ListItem(isHeader = false, milestone = m, isCurrent = isCurrent))
            }
        }
        items
    }

    // Find scroll target — the header of the current age group
    val scrollTargetIndex = remember(ageDays) {
        if (ageDays == null) 0
        else {
            val idx = listItems.indexOfFirst { it.isHeader && it.isCurrent }
            // Scroll a bit above so user sees context
            (if (idx > 0) idx - 1 else idx).coerceAtLeast(0)
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(scrollTargetIndex) {
        if (scrollTargetIndex > 0) {
            listState.scrollToItem(scrollTargetIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Development Milestones")
                        if (babyName != null && ageDays != null) {
                            Text(
                                text = "$babyName · ${formatAgeDays(ageDays)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (birthDate == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Set baby's birth date in Settings to see personalized milestones.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(listItems, key = { if (it.isHeader) "header_${it.ageLabel}" else "m_${it.milestone!!.title}_${it.milestone.ageLabel}" }) { item ->
                    if (item.isHeader) {
                        AgeHeader(
                            label = item.ageLabel,
                            ageDays = item.ageDays,
                            currentAgeDays = ageDays,
                            isCurrent = item.isCurrent
                        )
                    } else {
                        MilestoneCard(milestone = item.milestone!!, isPast = ageDays != null && ageDays >= item.milestone.ageDays)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AgeHeader(label: String, ageDays: Int, currentAgeDays: Int?, isCurrent: Boolean) {
    val daysUntil = if (currentAgeDays != null) ageDays - currentAgeDays else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Age indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        currentAgeDays != null && currentAgeDays >= ageDays -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "← YOU ARE HERE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        } else if (daysUntil != null && daysUntil > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "in ${formatDaysUntil(daysUntil)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneCard(milestone: Milestone, isPast: Boolean) {
    val categoryColor = categoryColors[milestone.category] ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPast) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji
            Text(
                text = milestone.emoji,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category badge
                    Text(
                        text = milestone.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = if (isPast) 0.5f else 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun formatAgeDays(days: Int): String {
    return when {
        days < 7 -> "${days}d"
        days < 30 -> "${days / 7}w ${days % 7}d"
        days < 365 -> {
            val months = days / 30
            val remainDays = days % 30
            if (remainDays > 0) "${months}m ${remainDays}d" else "${months}m"
        }
        else -> {
            val years = days / 365
            val months = (days % 365) / 30
            if (months > 0) "${years}y ${months}m" else "${years}y"
        }
    }
}

private fun formatDaysUntil(days: Int): String {
    return when {
        days < 7 -> "$days days"
        days < 30 -> "${days / 7} weeks"
        days < 365 -> {
            val months = days / 30
            "$months month${if (months > 1) "s" else ""}"
        }
        else -> {
            val years = days / 365
            "$years year${if (years > 1) "s" else ""}"
        }
    }
}
