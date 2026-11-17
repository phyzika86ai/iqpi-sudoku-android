"""Desktop reference test for mobile offline digit algorithm using the user's screenshot."""
from pathlib import Path
import cv2, numpy as np

BASE = Path(__file__).resolve().parents[1]
SHOT = BASE / 'verification/sample_grid_6x6.png'
TRUTH = np.array([
 [0,3,0,4,1,0],
 [4,1,2,0,0,0],
 [0,5,0,3,0,0],
 [0,0,3,0,2,0],
 [0,0,0,2,0,1],
 [0,2,4,0,3,0]
])

def extract(image_bgr):
    templates = {}
    for i in range(1,7):
        p = str(BASE/'app/src/main/assets/digits'/f'{i}.png')
        templates[i] = cv2.imread(p,cv2.IMREAD_GRAYSCALE)>127
    scaled=cv2.resize(image_bgr,(720,720),interpolation=cv2.INTER_LINEAR)
    result=np.zeros((6,6),dtype=int)
    report=[]
    for r in range(6):
        for c in range(6):
            crop=scaled[r*120+20:r*120+100,c*120+20:c*120+100]
            chmax=crop.max(axis=2); chmin=crop.min(axis=2)
            mask=np.uint8((chmin>=165)&((chmax-chmin)<=70))*255
            count, lab, stats,_=cv2.connectedComponentsWithStats(mask,8)
            if count<=1: continue
            best=max(range(1,count),key=lambda k:stats[k,cv2.CC_STAT_AREA])
            x,y,w,h,area=stats[best]
            if area<85 or h<18 or w<5: continue
            glyph=np.uint8(lab[y:y+h,x:x+w]==best)*255
            scale=min(32/w,32/h)
            nw=max(1,round(w*scale));nh=max(1,round(h*scale))
            res=np.zeros((nh,nw),dtype=bool)
            for yy in range(nh):
                for xx in range(nw):
                    res[yy,xx]=glyph[min(h-1,int(yy/scale)),min(w-1,int(xx/scale))]>0
            norm=np.zeros((40,40),dtype=bool)
            norm[(40-nh)//2:(40-nh)//2+nh,(40-nw)//2:(40-nw)//2+nw]=res
            scores=sorted([(np.count_nonzero(norm&t)/np.count_nonzero(norm|t),i) for i,t in templates.items()],reverse=True)
            score,pred=scores[0]
            result[r,c]=pred
            report.append((r+1,c+1,pred,round(score,3),round(score-scores[1][0],3)))
    return result, report

if __name__=='__main__':
    im=cv2.imread(str(SHOT))
    rect=im
    print('Reference photo size:',rect.shape)
    for width in [730,540,400,320,270]:
        mimic=cv2.resize(rect,(width,width),interpolation=cv2.INTER_AREA)
        result,report=extract(mimic)
        mismatches=np.argwhere(result!=TRUTH)
        print(f'Mimic camera grid {width}x{width}: {36-len(mismatches)}/36 cells correct; wrong={[(tuple(x+1),result[tuple(x)],TRUTH[tuple(x)]) for x in mismatches]}')
        if width==320:
            print('recognized',result.tolist());print('scores',report)
