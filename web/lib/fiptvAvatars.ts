/**
 * fIPTV: animierte Profilbilder – Microsoft Fluent Emoji Animated (MIT-Lizenz,
 * https://github.com/microsoft/fluentui-emoji-animated). Dateien in public/avatars/anim/.
 * IDs ab 101; ältere IDs mit passendem Motiv zeigen die animierte Version.
 */
export const ANIM_AVATAR_FIRST_ID = 101;
export const ANIM_AVATAR_SLUGS: string[] = ["cat_face", "dog_face", "fox", "owl", "penguin", "panda", "bear", "polar_bear", "rabbit_face", "hamster", "mouse_face", "frog", "monkey_face", "lion", "tiger_face", "koala", "raccoon", "hedgehog", "sloth", "otter", "wolf", "pig_face", "cow_face", "horse_face", "unicorn", "dragon_face", "t-rex", "dolphin", "octopus", "turtle", "butterfly", "parrot", "front-facing_baby_chick", "phoenix", "robot", "alien", "alien_monster", "ghost", "disguised_face", "skull", "smiling_face_with_sunglasses", "nerd_face", "clown_face", "cowboy_hat_face", "partying_face", "zany_face", "smiling_face_with_heart-eyes", "star-struck", "smiling_face_with_halo", "shushing_face", "smiling_face_with_horns", "ogre", "goblin", "cold_face", "exploding_head", "hugging_face", "face_with_monocle", "grinning_cat", "sleeping_face", "rocket", "flying_saucer", "fire", "rainbow", "snowman", "comet", "glowing_star", "ringed_planet", "sun_with_face", "full_moon_face", "jack-o-lantern", "teddy_bear", "crystal_ball", "party_popper", "birthday_cake", "hot_beverage", "musical_notes", "christmas_tree"];
export const ANIM_AVATAR_CATEGORIES: Array<{ label: string; ids: number[] }> = [{"label": "Tiere", "ids": [101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134]}, {"label": "Figuren", "ids": [135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159]}, {"label": "Spaß", "ids": [160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177]}];
const LEGACY: Record<number, number> = {"1": 101, "2": 102, "3": 103, "4": 104, "5": 105, "6": 106, "7": 135, "8": 136, "9": 138, "10": 139, "11": 140, "12": 137, "16": 160, "17": 166, "18": 162, "21": 125, "22": 126, "23": 127, "25": 107, "26": 109, "27": 110, "28": 112, "29": 113, "30": 114, "31": 115, "32": 116, "33": 111, "34": 128, "35": 129, "36": 131, "37": 130, "38": 117, "39": 118, "40": 141, "41": 142, "42": 143, "43": 144, "44": 145, "45": 146, "46": 147, "47": 148, "48": 149, "49": 150, "50": 170, "51": 152, "52": 153, "53": 154, "54": 155, "60": 172, "70": 163, "79": 165};
const GRADIENTS: Record<string, [string, string]> = {"Tiere": ["#1e1a12", "#2e2618"], "Figuren": ["#1a1630", "#28204a"], "Spaß": ["#0e1a2a", "#16283e"]};

export function resolveAnimAvatar(avatarId: number): number | null {
  if (avatarId >= ANIM_AVATAR_FIRST_ID && avatarId - ANIM_AVATAR_FIRST_ID < ANIM_AVATAR_SLUGS.length) return avatarId;
  return LEGACY[avatarId] ?? null;
}

export function animAvatarSrc(id: number, animated: boolean): string {
  const slug = ANIM_AVATAR_SLUGS[id - ANIM_AVATAR_FIRST_ID];
  return `/avatars/anim/${slug}${animated ? "" : "_still"}.webp`;
}

export function animAvatarGradient(id: number): [string, string] {
  const cat = ANIM_AVATAR_CATEGORIES.find((c) => c.ids.includes(id))?.label;
  return (cat && GRADIENTS[cat]) || ["#1a1a1a", "#2d2d2d"];
}
