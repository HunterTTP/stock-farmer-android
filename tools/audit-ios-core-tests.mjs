import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Explicit aliases record reviewed assertion equivalence where names differ or a case was split.
const aliases = {
  anAnimalGivesNothingUntilItEatsThenStaysFedAndIsNotHungryAgainUntilItIsHarvested: [
    'an animal gives nothing until it eats then stays fed and is not hungry again until harvested',
  ],
  petsHensAndLivestockLoadOldSavesWithoutMealsAndRoundTripTheirOwnMeals: [
    'pets hens and livestock round trip their own meals',
    'animal meals are omitted when absent and load unfed',
    'pets hens and livestock reject invalid meal dates',
  ],
  aFlightFlutterInGentleStepsAtALivelyPace: ['a flight flutters in gentle steps at a lively pace'],
  chickenCanBeNamedAndBlankNamesFallBack: ['chickens can be named and blank names fall back'],
  everyCropEarnsFivePercentMorePerMinuteThanThePreviousOneUpToZucchiniAndTwoAndAHalfAfter: [
    'every crop earns five percent more per minute than the previous one up to zucchini and less after',
  ],
  cheeseBatchConsumesMilkWaitsAndRetainsItsStateThroughSaving: [
    'a cheese batch consumes milk waits and retains its state through saving',
  ],
  aSprinklerSprayingDriesItsLandOutTileByTileWhenDestroyedOrMovedIsSavedAndOldSavesLoad: ['lingering water is saved'],
  aScarecrowStartsAtOnceAndASaveFromBeforeSprayingStillWaters: [
    'a scarecrow starts at once and an old sprinkler save still decodes',
  ],
  hensWithoutMealsKeepLayingAtTheNormalPace: ['hens keep laying at the normal pace'],
  opensActiveFarmAndPreparesItWithoutWriting: ['opens the active farm and prepares it without writing'],
  emptySlotGeneratesARealFarmWithoutSavingIt: ['an empty slot generates a real farm without saving it'],
  everyDecorationOptionHasBundledArtAndAReachableUnlock: [
    'every decoration option has a reachable unlock and sane sizes',
    'every decoration and variant size exists across all seasons',
  ],
  everyLevelUnlocksSomeDecorationAndNoLevelUnlocksMoreThanTwo: [
    'every level unlocks some decoration and no level unlocks more than three',
  ],
  theChoiceSurvivesSavesAndOlderSavesPayAsBefore: ['the fertilizer choice survives saves'],
  aFlightSetsOffFromBehindSomethingAtTheEdgeAndEndsBehindSomethingNearerTheMiddle: [
    'a flight sets off from behind something at the edge and ends nearer the middle',
  ],
  aFlightTakesALongGentleTimeAtAWalkingPaceOfAFewTenthsOfATilePerSecond: [
    'a flight takes a long gentle time at a walking pace',
  ],
  everyFruitTreeHasItsTwoPicturesAndBasket: ['every crop and fruit tree asset exists across all phases and seasons'],
  remainingGrowTimeIsNilWithoutACropAndCountsDownWithOne: [
    'remaining grow time is null without a crop and counts down with one',
  ],
  everyCropBuildingDecorationTreeAndUpgradeIsListedAtItsOwnLevelAndOnlyThere: [
    'everything is listed at its own level and only there',
  ],
  sellingPaysForAgeAndTheTimerStartsAfreshOnAFullBuilding: [
    'releasing earns nothing and the timer starts afresh on a full building',
  ],
  grownDonkeysDoNotAddStorageAndCanBeSoldWhileOldStorageIsOverCapacity: [
    'grown donkeys do not add storage and can be released while old storage is over capacity',
  ],
  existingLargeStandsRemainUpgradedAndCountAgainstPlacementLimit: [
    'existing large stands remain upgraded and count against the placement limit',
  ],
  movingKeepsTheUpgradeAndRefundIncludesOnlyItsCashCost: [
    'moving keeps the upgrade and the refund includes only its cash cost',
  ],
  claimingRequiresEveryObjectivePaysOnceAndReplacesQuest: [
    'claiming requires every objective pays once and replaces the quest',
  ],
  burstCoalescesAndFlushPersistsTheLatestStateInEachSlot: [
    'a burst coalesces and a flush persists the latest state in each slot',
  ],
  flushReportsAnUnsupportedSaveWithoutReplacingIt: ['a flush reports an unsupported save without replacing it'],
  aFreshFolderHasFourEmptySlotsAndTheFirstIsActive: ['a fresh folder has empty slots and the first is active'],
  loadReturnsNilWhenNothingSaved: ['load returns null when nothing saved'],
  unknownDecorationDoesNotBecomeAnUnrelatedItem: ['an unknown decoration does not become an unrelated item'],
  springKeepsItsOriginalSongsAndEveryBundledRecordingHasOneSeason: [
    'spring keeps its original songs', 'every recording has one season', 'retired recordings stay out',
    'fall and winter keep their own folders', 'bundled recordings match the seasonal soundtrack',
  ],
  theChoiceSurvivesSavesAndOlderSavesWithoutItLoad: ['the sprinkler choice survives saves and older saves without it load'],
  theLedgerSurvivesASaveAndAnOldSaveStillLoads: ['the ledger survives a save'],
  movingTractorIsQueryableAndBlocksPlacement: ['a moving tractor is queryable and blocks placement'],
  everyColorOfEverySpeciesAndSizeHasAPicture: ['every tree species size and color exists across all seasons'],
  bothTiersHaveTheSameUnlockLevelAndPlacementLimit: ['both market tiers have the same unlock level and placement limit'],
};

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ios = path.resolve(process.argv[2] || path.join(root, '../stock-farmer-ios'),
  'StockFarmer/Core/Tests/StockFarmerCoreTests');
const walk = folder => fs.readdirSync(folder, { withFileTypes: true }).flatMap(entry =>
  entry.isDirectory() ? walk(path.join(folder, entry.name)) : [path.join(folder, entry.name)]);
const normalize = name => name.toLowerCase().replace(/[^a-z0-9]/g, '');
const kotlin = ['core/src/test', 'app/src/test'].flatMap(folder => walk(path.join(root, folder)))
  .filter(file => file.endsWith('.kt')).flatMap(file => {
    const source = fs.readFileSync(file, 'utf8');
    return [...source.matchAll(/fun\s+(?:`([^`]+)`|(\w+))\s*\(/g)]
      .filter(match => /@(Test|ParameterizedTest)\b/.test(source.slice(
        source.lastIndexOf('fun ', match.index - 1) + 1, match.index)))
      .map(match => ({ file: path.relative(root, file).replaceAll('\\', '/'),
        name: match[1] || match[2], key: normalize(match[1] || match[2]) }));
  });
const skipped = new Set(['RetiredCosmeticsTests.swift', 'RetiredDecorationTests.swift', 'RetiredFeedingTests.swift']);
const suites = walk(ios).filter(file => file.endsWith('.swift') && !skipped.has(path.basename(file)))
  .sort().map(file => ({ name: path.basename(file), cases: [...fs.readFileSync(file, 'utf8')
    .matchAll(/@Test(?:\([^]*?\))?\s*func\s+(\w+)/g)].map(match => {
      const name = match[1];
      const targets = (aliases[name] || [name]).flatMap(alias => {
        const matches = kotlin.filter(test => test.key === normalize(alias));
        if (matches.length === 0) throw new Error(`${path.basename(file)}:${name}: missing Kotlin case ${alias}`);
        return matches;
      });
      return { name, targets };
    }) })).filter(suite => suite.cases.length);
const total = suites.reduce((count, suite) => count + suite.cases.length, 0);
if (process.argv.includes('--cases')) {
  console.log(JSON.stringify(suites, null, 2));
} else {
  console.log(`${total} Swift cases in ${suites.length} suites mapped; only the three Retired* suites excluded.\n`);
  console.log('| Swift file | Cases | Android test files |\n|---|---:|---|');
  for (const suite of suites) {
    const files = [...new Set(suite.cases.flatMap(test => test.targets.map(target => target.file)))];
    console.log(`| ${suite.name} | ${suite.cases.length} | ${files.map(file => `[${path.basename(file)}](../${file})`).join(', ')} |`);
  }
}
